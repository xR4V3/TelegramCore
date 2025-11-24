package modules.parsers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class XmlExporter {

    /**
     * products         – список товаров, который вернул LeroyMerlinParser
     * firstName/secondName/thirdName – категории (можешь оставить "" если нет)
     * groupLink        – ссылка на категорию (как в твоём примере)
     * outputDir        – куда класть файл
     */
    public static File writeGroupToFile(List<Product> products,
                                        String groupLink,
                                        File outputDir) throws Exception {

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File outFile = new File(outputDir, ts + ".xml");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

// <group>
        Element group = doc.createElement("group");
        doc.appendChild(group);

// Берем категории из первого товара (если список не пуст)
        String firstNameVal = "";
        String secondNameVal = "";
        String thirdNameVal = "";

        if (products != null && !products.isEmpty()) {
            Product first = products.get(0);
            if (first.getCategoryGroupName() != null) {
                firstNameVal = first.getCategoryGroupName();
            }
            if (first.getCategoryName() != null) {
                secondNameVal = first.getCategoryName();
            }
            if (first.getSubcategoryName() != null) {
                thirdNameVal = first.getSubcategoryName();
            }
        }

// <first_name>
        Element fn = doc.createElement("first_name");
        fn.setTextContent(firstNameVal);
        group.appendChild(fn);

// <second_name>
        Element sn = doc.createElement("second_name");
        sn.setTextContent(secondNameVal);
        group.appendChild(sn);

// <third_name>
        Element tn = doc.createElement("third_name");
        tn.setTextContent(thirdNameVal);
        group.appendChild(tn);

// <link> – ссылка на категорию/группу
        Element glink = doc.createElement("link");
        glink.setTextContent(groupLink != null ? groupLink : "");
        group.appendChild(glink);

        // <goods>
        Element goods = doc.createElement("goods");
        group.appendChild(goods);

        for (Product p : products) {
            Element good = doc.createElement("good");
            goods.appendChild(good);

            // <code>
            Element code = doc.createElement("code");
            code.setTextContent(p.getId());
            good.appendChild(code);

            // <link> – ссылка на товар
            Element link = doc.createElement("link");
            link.setTextContent(p.getUrl());
            good.appendChild(link);

            // <price> – возьмём спеццену если есть, иначе обычную
            BigDecimal finalPrice = p.getSpecialPrice() != null && p.getSpecialPrice().compareTo(BigDecimal.ZERO) > 0
                    ? p.getSpecialPrice()
                    : p.getPrice();
            Element price = doc.createElement("price");
            price.setTextContent(finalPrice != null ? finalPrice.toPlainString() : "0");
            good.appendChild(price);

            // <unit>
            Element unit = doc.createElement("unit");
            unit.setTextContent(p.getUnit() != null ? p.getUnit() : "");
            good.appendChild(unit);

            // <rest> – общий остаток
            Element rest = doc.createElement("rest");
            rest.setTextContent(String.valueOf(p.getTotalStock()));
            good.appendChild(rest);

            // <name>
            Element name = doc.createElement("name");
            name.setTextContent(p.getName() != null ? p.getName() : "");
            good.appendChild(name);

            // <description>
            Element desc = doc.createElement("description");
            desc.setTextContent(p.getDescription() != null ? p.getDescription() : "");
            good.appendChild(desc);

            // <main_photo>
            Element mainPhoto = doc.createElement("main_photo");
            mainPhoto.setTextContent(p.getImageUrl() != null ? p.getImageUrl() : "");
            good.appendChild(mainPhoto);

            // <photos> – JSON-массив строк, как у тебя
            Element photos = doc.createElement("photos");
            if (p.getAllImages() != null && !p.getAllImages().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                for (int i = 0; i < p.getAllImages().size(); i++) {
                    if (i > 0) sb.append(", ");
                    // ИСПРАВЛЕНИЕ: используем прямые кавычки, XML сам их экранирует
                    sb.append("\"").append(p.getAllImages().get(i)).append("\"");
                }
                sb.append("]");
                photos.setTextContent(sb.toString());
            } else {
                photos.setTextContent("[]");
            }
            good.appendChild(photos);

            // <brand>
            Element brand = doc.createElement("brand");
            brand.setTextContent(p.getBrand() != null ? p.getBrand() : "");
            good.appendChild(brand);

            // <country>
            Element country = doc.createElement("country");
            country.setTextContent(p.getCountry() != null ? p.getCountry() : "");
            good.appendChild(country);

            // <weight>
            Element weight = doc.createElement("weight");
            weight.setTextContent(p.getWeight() != null ? p.getWeight() : "");
            good.appendChild(weight);

            // <characteristics>
            Element chars = doc.createElement("characteristics");
            chars.setTextContent(p.getCharacteristicsText() != null ? p.getCharacteristicsText() : "");
            good.appendChild(chars);

            // <alsobuy> – пока пусто, или можешь писать "[]"
            Element alsobuy = doc.createElement("alsobuy");
            alsobuy.setTextContent("[]");
            good.appendChild(alsobuy);
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        t.transform(new DOMSource(doc), new StreamResult(outFile));

        System.out.println("XML сохранён в " + outFile.getAbsolutePath());
        return outFile;
    }
}
