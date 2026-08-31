package top.boluofan.musictv.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class BackupRulesContractTest {
    private static final Set<String> PRIVATE_DATA = new LinkedHashSet<>(Arrays.asList(
            "sharedpref:LxMusicPrefs.xml",
            "sharedpref:MugglesBackendPrefs.xml",
            "sharedpref:MugglesSourcePrefs.xml",
            "file:sources/"));

    @Test
    public void manifestKeepsBackupEnabledAndReferencesBothRuleFormats() throws Exception {
        Element application = firstElement(parse("src/main/AndroidManifest.xml"), "application");

        assertEquals("true", androidAttribute(application, "allowBackup"));
        assertEquals("@xml/backup_rules", androidAttribute(application, "fullBackupContent"));
        assertEquals("@xml/data_extraction_rules",
                androidAttribute(application, "dataExtractionRules"));
    }

    @Test
    public void legacyBackupExcludesConnectionCredentialsAndSourceScripts() throws Exception {
        Document rules = parse("src/main/res/xml/backup_rules.xml");

        assertTrue(entries(rules.getDocumentElement(), "include").isEmpty());
        assertEquals(PRIVATE_DATA, entries(rules.getDocumentElement(), "exclude"));
    }

    @Test
    public void android12RulesProtectCloudBackupAndDeviceTransfer() throws Exception {
        Document rules = parse("src/main/res/xml-v31/data_extraction_rules.xml");

        for (String sectionName : Arrays.asList("cloud-backup", "device-transfer")) {
            Element section = firstElement(rules, sectionName);
            assertTrue(sectionName, entries(section, "include").isEmpty());
            assertEquals(sectionName, PRIVATE_DATA, entries(section, "exclude"));
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

    private static Element firstElement(Document document, String name) {
        NodeList nodes = document.getElementsByTagName(name);
        assertTrue("Missing " + name, nodes.getLength() > 0);
        return (Element) nodes.item(0);
    }

    private static String androidAttribute(Element element, String name) {
        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", name);
        assertNotNull(value);
        return value;
    }

    private static Set<String> entries(Element parent, String tagName) {
        Set<String> result = new LinkedHashSet<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE || !tagName.equals(child.getNodeName())) {
                continue;
            }
            Element element = (Element) child;
            result.add(element.getAttribute("domain") + ":" + element.getAttribute("path"));
        }
        return result;
    }
}
