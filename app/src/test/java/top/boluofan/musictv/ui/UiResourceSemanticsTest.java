package top.boluofan.musictv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class UiResourceSemanticsTest {
    @Test
    public void reversibleInputClearsShareTheCloseIcon() throws Exception {
        assertEquals("@drawable/ic_close", androidAttribute(
                byId(parse("src/main/res/layout/fragment_search.xml"), "btnClear"), "src"));
        assertEquals("@drawable/ic_close", androidAttribute(
                byId(parse("src/main/res/layout/activity_playlist_import.xml"),
                        "btnImportClear"), "src"));
    }

    @Test
    public void dialogThemeDoesNotPaintEveryInternalPanel() throws Exception {
        Element style = style(parse("src/main/res/values/themes.xml"), "CustomAlertDialog");
        NodeList items = style.getElementsByTagName("item");
        for (int index = 0; index < items.getLength(); index++) {
            assertFalse("android:background".equals(
                    ((Element) items.item(index)).getAttribute("name")));
        }
    }

    private static Document parse(String relativePath) throws Exception {
        Path modulePath = Paths.get(relativePath);
        Path rootPath = Paths.get("app").resolve(modulePath);
        Path sourcePath = Files.exists(modulePath) ? modulePath : rootPath;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(sourcePath.toFile());
    }

    private static Element byId(Document document, String id) {
        NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (("@+id/" + id).equals(androidAttribute(element, "id"))) return element;
        }
        throw new AssertionError("Missing view " + id);
    }

    private static Element style(Document document, String name) {
        NodeList nodes = document.getElementsByTagName("style");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element element = (Element) nodes.item(index);
            if (name.equals(element.getAttribute("name"))) return element;
        }
        throw new AssertionError("Missing style " + name);
    }

    private static String androidAttribute(Element element, String name) {
        return element.getAttributeNS("http://schemas.android.com/apk/res/android", name);
    }
}
