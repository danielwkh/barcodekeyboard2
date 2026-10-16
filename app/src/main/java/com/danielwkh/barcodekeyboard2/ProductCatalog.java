package com.danielwkh.barcodekeyboard2;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

final class ProductCatalog {
    private static final String PREFS = "product_catalog";
    private static final String DATA = "barcode_names";
    private ProductCatalog() {}

    static synchronized String nameFor(Context context, String barcode) {
        try { return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}" )).optString(barcode, ""); }
        catch (Exception e) { return ""; }
    }
    static synchronized int count(Context context) {
        try { return new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}" )).length(); }
        catch (Exception e) { return 0; }
    }

    static int importWorkbook(Context context, Uri uri) throws Exception {
        byte[] stringsXml = null, sheetXml = null;
        int totalBytes = 0;
        InputStream opened = context.getContentResolver().openInputStream(uri);
        if (opened == null) throw new IllegalArgumentException("無法讀取所選檔案");
        try (InputStream source = opened; ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("xl/sharedStrings.xml") || name.equals("xl/worksheets/sheet1.xml")) {
                    byte[] bytes = readEntry(zip, 32 * 1024 * 1024);
                    totalBytes += bytes.length;
                    if (totalBytes > 48 * 1024 * 1024) throw new IllegalArgumentException("XLSX 內容過大，請選擇較小的檔案");
                    if (name.equals("xl/sharedStrings.xml")) stringsXml = bytes; else sheetXml = bytes;
                }
                zip.closeEntry();
            }
        }
        if (sheetXml == null) throw new IllegalArgumentException("找不到活頁簿第一張工作表；請使用標準 XLSX 檔");
        List<String> shared = stringsXml == null ? new ArrayList<>() : parseSharedStrings(stringsXml);
        List<Map<Integer, String>> rows = parseRows(sheetXml, shared);
        if (rows.isEmpty()) throw new IllegalArgumentException("工作表沒有資料");
        Map<Integer, String> headers = rows.get(0);
        int barcodeCol = findHeader(headers, new String[]{"barcode", "ean", "upc", "條碼", "商品條碼", "條碼編號", "sku"});
        int nameCol = findHeader(headers, new String[]{"productname", "product", "name", "品名", "商品名稱", "產品名稱", "商品"});
        if (barcodeCol < 0 || nameCol < 0) throw new IllegalArgumentException("第一列請包含條碼欄與商品名稱欄，例如：Barcode、Product Name（或 條碼、商品名稱）");
        JSONObject catalog = new JSONObject(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DATA, "{}"));
        int imported = 0;
        for (int i = 1; i < rows.size(); i++) {
            Map<Integer, String> row = rows.get(i);
            String code = row.getOrDefault(barcodeCol, "").trim();
            String title = row.getOrDefault(nameCol, "").trim();
            if (!code.isEmpty() && !title.isEmpty()) { catalog.put(code, title); imported++; }
        }
        if (imported == 0) throw new IllegalArgumentException("沒有找到有效的條碼與商品名稱資料列");
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(DATA, catalog.toString()).putInt("last_import_count", imported).apply();
        return imported;
    }

    private static byte[] readEntry(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read; int size = 0;
        while ((read = input.read(buffer)) != -1) { size += read; if (size > limit) throw new IllegalArgumentException("XLSX 工作表超過 32 MB"); out.write(buffer, 0, read); }
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
    private static List<Map<Integer, String>> parseRows(byte[] xml, List<String> shared) throws Exception {
        List<Map<Integer, String>> rows = new ArrayList<>(); XmlPullParser parser = Xml.newPullParser(); parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        Map<Integer, String> row = null; int event, col = -1; String type = ""; StringBuilder value = null;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.getName().equals("row")) row = new TreeMap<>();
            else if (event == XmlPullParser.START_TAG && parser.getName().equals("c") && row != null) {
                col = columnIndex(parser.getAttributeValue(null, "r")); type = parser.getAttributeValue(null, "t"); value = new StringBuilder();
            } else if (event == XmlPullParser.START_TAG && value != null && (parser.getName().equals("v") || parser.getName().equals("t"))) {
                value.append(parser.nextText());
            } else if (event == XmlPullParser.END_TAG && parser.getName().equals("c") && row != null && value != null) {
                String cell = value.toString();
                if ("s".equals(type)) { try { cell = shared.get(Integer.parseInt(cell)); } catch (Exception ignored) { cell = ""; } }
                if (col >= 0) row.put(col, cell);
                value = null; col = -1; type = "";
            } else if (event == XmlPullParser.END_TAG && parser.getName().equals("row") && row != null) { rows.add(row); row = null; }
        }
        return rows;
    }
    private static int columnIndex(String ref) {
        if (ref == null) return -1; int value = 0, i = 0;
        while (i < ref.length() && Character.isLetter(ref.charAt(i))) value = value * 26 + Character.toUpperCase(ref.charAt(i++)) - 'A' + 1;
        return i == 0 ? -1 : value - 1;
    }
    private static int findHeader(Map<Integer, String> headers, String[] aliases) {
        for (Map.Entry<Integer, String> cell : headers.entrySet()) {
            String value = cell.getValue().toLowerCase().replaceAll("[\\s_\\-（）()]+", "");
            for (String alias : aliases) if (value.equals(alias.toLowerCase().replaceAll("[\\s_\\-（）()]+", ""))) return cell.getKey();
        }
        return -1;
    }
}
