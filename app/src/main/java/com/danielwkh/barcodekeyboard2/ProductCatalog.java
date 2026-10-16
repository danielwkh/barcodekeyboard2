package com.danielwkh.barcodekeyboard2;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Local-only product barcode/name catalog. The selected workbook is parsed in memory and never bundled. */
final class ProductCatalog {
    private static final String PREFS = "product_catalog";
    private static final String DATA = "barcode_names";
    private ProductCatalog() {}

    static synchronized String nameFor(Context context, String barcode) {
        try { return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}")).optString(barcode, ""); }
        catch (Exception e) { return ""; }
    }
    static synchronized int count(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}")).length(); }
        catch (Exception e) { return 0; }
    }

    static int importWorkbook(Context context, Uri uri) throws Exception {
        byte[] stringsXml = null, productSheetXml = null;
        int totalBytes = 0;
        InputStream opened = context.getContentResolver().openInputStream(uri);
        if (opened == null) throw new IllegalArgumentException("无法读取所选文件");
        try (InputStream source = opened; ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                // 此商品模板的第一张「商品」工作表是 sheet1；第二张「店铺库位」不参与条码映射。
                if (name.equals("xl/sharedStrings.xml") || name.equals("xl/worksheets/sheet1.xml")) {
                    byte[] bytes = readEntry(zip, 48 * 1024 * 1024);
                    totalBytes += bytes.length;
                    if (totalBytes > 64 * 1024 * 1024) throw new IllegalArgumentException("XLSX 内容过大，请选较小的文件");
                    if (name.equals("xl/sharedStrings.xml")) stringsXml = bytes; else productSheetXml = bytes;
                }
                zip.closeEntry();
            }
        }
        if (productSheetXml == null) throw new IllegalArgumentException("找不到第一张商品工作表，请选择标准 XLSX 文件");
        List<String> shared = stringsXml == null ? new ArrayList<>() : parseSharedStrings(stringsXml);
        JSONObject catalog = new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}"));
        int importedCodes = importProductRows(productSheetXml, shared, catalog);
        if (importedCodes == 0) throw new IllegalArgumentException("没有找到有效的条码与商品名称资料");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DATA, catalog.toString()).putInt("last_import_count", importedCodes).apply();
        return importedCodes;
    }

    private static int importProductRows(byte[] xml, List<String> shared, JSONObject catalog) throws Exception {
        XmlPullParser parser = Xml.newPullParser(); parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        Map<Integer, String> row = null; int column = -1, event, nameColumn = -1, imported = 0;
        String cellType = ""; StringBuilder value = null; boolean headerRead = false; List<Integer> barcodeColumns = new ArrayList<>();
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.getName().equals("row")) row = new TreeMap<>();
            else if (event == XmlPullParser.START_TAG && parser.getName().equals("c") && row != null) {
                column = columnIndex(parser.getAttributeValue(null, "r")); cellType = parser.getAttributeValue(null, "t"); value = new StringBuilder();
            } else if (event == XmlPullParser.START_TAG && value != null && (parser.getName().equals("v") || parser.getName().equals("t"))) {
                value.append(parser.nextText());
            } else if (event == XmlPullParser.END_TAG && parser.getName().equals("c") && row != null && value != null) {
                boolean needed = !headerRead || column == nameColumn || barcodeColumns.contains(column);
                if (needed && column >= 0) {
                    String cell = value.toString();
                    if ("s".equals(cellType)) { try { cell = shared.get(Integer.parseInt(cell)); } catch (Exception ignored) { cell = ""; } }
                    row.put(column, cell);
                }
                value = null; column = -1; cellType = "";
            } else if (event == XmlPullParser.END_TAG && parser.getName().equals("row") && row != null) {
                if (!headerRead) {
                    barcodeColumns = findHeaders(row, new String[]{"barcode", "bar code", "ean", "upc", "條碼", "条码", "商品條碼", "商品条码", "條碼編號", "条码编号", "第二條碼", "第二条码"});
                    if (barcodeColumns.isEmpty()) barcodeColumns = findHeaders(row, new String[]{"sku"});
                    nameColumn = findHeader(row, new String[]{"product name", "productname", "product", "name", "名稱", "名称", "商品名稱", "商品名称", "產品名稱", "产品名称", "品名", "商品"});
                    if (barcodeColumns.isEmpty() || nameColumn < 0) throw new IllegalArgumentException("第一张工作表首行须有「名稱」及「條碼」栏；可另含「第二條碼」栏");
                    headerRead = true;
                } else {
                    String title = row.getOrDefault(nameColumn, "").trim();
                    if (!title.isEmpty()) for (int barcodeColumn : barcodeColumns) {
                        String code = row.getOrDefault(barcodeColumn, "").trim();
                        if (!code.isEmpty()) { catalog.put(code, title); imported++; }
                    }
                }
                row = null;
            }
        }
        if (!headerRead) throw new IllegalArgumentException("商品工作表没有标题列");
        return imported;
    }

    private static byte[] readEntry(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read, size = 0;
        while ((read = input.read(buffer)) != -1) { size += read; if (size > limit) throw new IllegalArgumentException("XLSX 工作表超出大小限制"); out.write(buffer, 0, read); }
        return out.toByteArray();
    }
    private static List<String> parseSharedStrings(byte[] xml) throws Exception {
        List<String> result = new ArrayList<>(); XmlPullParser parser = Xml.newPullParser(); parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder item = null; int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.getName().equals("si")) item = new StringBuilder();
            else if (event == XmlPullParser.START_TAG && parser.getName().equals("t") && item != null) item.append(parser.nextText());
            else if (event == XmlPullParser.END_TAG && parser.getName().equals("si") && item != null) { result.add(item.toString()); item = null; }
        }
        return result;
    }
    private static int columnIndex(String ref) {
        if (ref == null) return -1; int value = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) value = value * 26 + Character.toUpperCase(ref.charAt(i++)) - 'A' + 1;
        return i == 0 ? -1 : value - 1;
    }
    private static List<Integer> findHeaders(Map<Integer, String> headers, String[] aliases) {
        List<Integer> columns = new ArrayList<>();
        for (Map.Entry<Integer, String> cell : headers.entrySet()) {
            String value = normalize(cell.getValue());
            for (String alias : aliases) if (value.equals(normalize(alias))) { columns.add(cell.getKey()); break; }
        }
        return columns;
    }
    private static int findHeader(Map<Integer, String> headers, String[] aliases) {
        List<Integer> columns = findHeaders(headers, aliases); return columns.isEmpty() ? -1 : columns.get(0);
    }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase().replaceAll("[\\s_\\-（）()]+", ""); }
}
