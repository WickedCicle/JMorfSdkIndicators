import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import ru.textanalysis.tawt.jmorfsdk.JMorfSdk;
import ru.textanalysis.tawt.jmorfsdk.JMorfSdkFactory;
import ru.textanalysis.tawt.ms.grammeme.MorfologyParameters;
import ru.textanalysis.tawt.ms.grammeme.MorfologyParametersHelper;
import ru.textanalysis.tawt.ms.model.jmorfsdk.Form;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class JMorfSdkExample {

    public static void main(String[] args) throws Exception {

        // Список ключ, значение для перевода тегов из JMorfSdk в НКРЯ
        HashMap<String, String> tags = new HashMap<>();

        fillTags(tags);

        // Список размеченных текстов для анализа
        ArrayList<String> texts = new ArrayList<>();

        // Заполняем список полных путей до файлов xhtml с разметкой
        Files.walk(Paths.get("D:\\Downloads\\RNC_million\\RNC_million\\sample_ar\\TEXTS"))
                .filter(Files::isRegularFile)
                .forEach((file -> {
                    texts.add(file.toString());
                }));

        JMorfSdk jMorfSdk = JMorfSdkFactory.loadFullLibrary();

        AtomicInteger intUnfamilliar = new AtomicInteger(); // ненайденные в словаре
        AtomicInteger intKnown = new AtomicInteger(); // найденные в словаре
        AtomicInteger wordCount = new AtomicInteger(); // суммарное количесвто слов
        AtomicInteger accuracy = new AtomicInteger(); // точно определённые слова
        AtomicInteger morphAccuracy = new AtomicInteger(); // первая же форма с подходящими морфологическими хар-ками
        AtomicInteger allFormsMorphAccuracy = new AtomicInteger(); // в словаре есть форма с подходящими морфологическими хар-ками
        AtomicBoolean isAdded = new AtomicBoolean(false);
        AtomicInteger tryNumber = new AtomicInteger(); // учёт первой проверенной формы

        Instant start;
        Instant finish;
        long elapsed = 0;

        HashSet<Form> set;

        try {
            for (String text : texts) {
                System.out.println("next file" + text);
                DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(text);

                Node html = document.getDocumentElement();

                NodeList htmlProps = html.getChildNodes();
                for (int i = 0; i < htmlProps.getLength(); i++) {
                    Node body = htmlProps.item(i);
                    if (body.getNodeType() != Node.TEXT_NODE && body.getNodeName().equals("body")) {
                        NodeList bodyProps = body.getChildNodes();
                        for (int j = 0; j < bodyProps.getLength(); j++) {
                            Node paragraph = bodyProps.item(j);
                            if (paragraph.getNodeType() != Node.TEXT_NODE && (paragraph.getNodeName().equals("p") || paragraph.getNodeName().equals("speach"))) {
                                NodeList paragraphProps = paragraph.getChildNodes();
                                for (int k = 0; k < paragraphProps.getLength(); k++) {
                                    Node sentence = paragraphProps.item(k);
                                    if (sentence.getNodeType() != Node.TEXT_NODE && sentence.getNodeName().equals("se")) {
                                        NodeList sentenceProps = sentence.getChildNodes();
                                        for (int m = 0; m < sentenceProps.getLength(); m++) {
                                            Node word = sentenceProps.item(m);
                                            if (word.getNodeType() != Node.TEXT_NODE && word.getNodeName().equals("w")) {
                                                wordCount.getAndIncrement();
                                                NodeList wordProps = word.getChildNodes();
                                                start = Instant.now();
                                                // Получаем одну из харакатеристик для словоформы
                                                set = new HashSet<>(jMorfSdk.getOmoForms(word.getTextContent().toLowerCase(Locale.ROOT).replaceAll("[` ]", "")));
                                                for (int n = 0; n < wordProps.getLength(); n++) {
                                                    Node characteristics = wordProps.item(n);
                                                    if (isAdded.get()) {
                                                        continue;
                                                    }
                                                    if (characteristics.getNodeType() != Node.TEXT_NODE && characteristics.getNodeName().equals("ana")) {
                                                        if (jMorfSdk.isFormExistsInDictionary(word.getTextContent().toLowerCase(Locale.ROOT).replaceAll("[` ]", ""))) {
                                                            intKnown.getAndIncrement();
                                                            set.forEach((form) -> {
                                                                if (!isAdded.get()) {
                                                                    // Проверяем соотвествие начальной формы из корпуса и из библиотеки
                                                                    if (Objects.equals(characteristics.getAttributes().getNamedItem("lex").getNodeValue().toLowerCase(Locale.ROOT).replaceAll("ё", "е"), form.getInitialForm().getInitialFormString().replaceAll("ё", "е"))) {
                                                                        accuracy.getAndIncrement();
                                                                        isAdded.set(true);
                                                                    }
                                                                }
                                                            });

                                                            final String[] tag = {""};

                                                            isAdded.set(false);

                                                            jMorfSdk.getOmoForms(word.getTextContent().toLowerCase(Locale.ROOT).replaceAll("[` ]", "")).forEach((form) -> {
                                                                if (!isAdded.get()) {
                                                                    isAdded.set(true);
                                                                    // Переводим список морфологических характеристик из библиотеки с стандарт НКРЯ
                                                                    tag[0] = "";
                                                                    tag[0] += tags.get(String.valueOf(form.getTypeOfSpeech()));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Animacy.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Gender.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Numbers.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Case.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.View.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Transitivity.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Liso.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Time.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Mood.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Act.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Voice.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Alone.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.TerminationForm.IDENTIFIER)));
                                                                    tag[0] += ",";
                                                                    tag[0] += tags.get(MorfologyParametersHelper.getParametersName(form.getMorfCharacteristicsByIdentifier(MorfologyParameters.Name.IDENTIFIER)));

                                                                    String[] ourTags = tag[0].split("[,]");

                                                                    // Убираем пустые и null значения из массива
                                                                    List<String> list = new ArrayList<>();
                                                                    for (String s : ourTags) {
                                                                        if (s != null && !Objects.equals(s, "null") && s.length() > 0) {
                                                                            list.add(s);
                                                                        }
                                                                    }
                                                                    ourTags = list.toArray(new String[0]);

                                                                    String[] markTags = characteristics.getAttributes().getNamedItem("gr").getNodeValue()
                                                                            .replaceAll("-PRO", "").replaceAll("PRO", "")
                                                                            .replaceAll("distort", "").replaceAll("persn", "")
                                                                            .replaceAll("patrn", "").replaceAll("indic", "")
                                                                            .replaceAll("imper", "").replaceAll("abbr", "")
                                                                            .replaceAll("ciph", "").replaceAll("INIT", "")
                                                                            .replaceAll("anom", "").replaceAll("famn", "")
                                                                            .replaceAll("zoon", "").replaceAll("pass", "")
                                                                            .replaceAll("inan", "").replaceAll("anim", "")
                                                                            .replaceAll("intr", "").replaceAll("tran", "")
                                                                            .replaceAll("act", "").replaceAll("ipf", "")
                                                                            .replaceAll("med", "").replaceAll("pf", "")
                                                                            .split("[,=]");

                                                                    // Убираем пустые и null значения из массива
                                                                    list = new ArrayList<>();
                                                                    for (String s : markTags) {
                                                                        if (s != null && !Objects.equals(s, "null") && s.length() > 0) {
                                                                            list.add(s);
                                                                        }
                                                                    }
                                                                    markTags = list.toArray(new String[0]);

                                                                    // Проверяем соответсвие тегов между библиотекой и НКРЯ
                                                                    for (String markTag : markTags) {
                                                                        if (!Arrays.asList(ourTags).contains(markTag)) {
                                                                            isAdded.set(false);
                                                                        }
                                                                    }

                                                                    if (isAdded.get()){
                                                                        // Если первая форма
                                                                        if (tryNumber.get() == 0) {
                                                                            morphAccuracy.getAndIncrement();
                                                                        }
                                                                        allFormsMorphAccuracy.getAndIncrement();
                                                                    }

                                                                    tryNumber.getAndIncrement();
                                                                }});

                                                            tryNumber.set(0);
                                                        } else {
                                                            intUnfamilliar.getAndIncrement();
                                                        }
                                                        isAdded.set(true);
                                                    }
                                                }
                                                finish = Instant.now();
                                                elapsed += Duration.between(start, finish).toMillis();
                                                isAdded.set(false);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            System.out.println("Количество ненайдённых: " + intUnfamilliar);
            System.out.println("Количество найдённых в словаре: " + intKnown);
            System.out.println("Общее количество слов: " + wordCount);
            System.out.println("Точно определенных начальных форм слов: " + accuracy);
            System.out.println("Точно определенных форм слов с полными характеристиками: " + morphAccuracy);
            System.out.println("Процент ненайдённых:" + intUnfamilliar.doubleValue()/wordCount.doubleValue());
            System.out.println("Точность начальных форм: " + accuracy.doubleValue()/intKnown.doubleValue());
            System.out.println("Точность определения характеристик первой формы: " + morphAccuracy.doubleValue()/intKnown.doubleValue());
            System.out.println("Точность определения характеристик всех форм: " + allFormsMorphAccuracy.doubleValue()/intKnown.doubleValue());
            System.out.println("Затраченное время: " + (double)elapsed/1000 + " секунд");

        } catch (ParserConfigurationException | SAXException | IOException ex) {
            ex.printStackTrace(System.out);
        }

        jMorfSdk.finish();
    }

    static void fillTags(HashMap<String, String> tags){
        tags.put("ANIMATE", "anim");
        tags.put("INANIMATE", "inan");
        tags.put("COMMON", "m-f");
        tags.put("MANS", "m");
        tags.put("FEMININ", "f");
        tags.put("NEUTER", "n");
        tags.put("SINGULAR", "sg");
        tags.put("PLURAL", "pl");
        tags.put("NOMINATIVE", "nom");
        tags.put("GENITIVE", "gen");
        tags.put("GENITIVE1", "gen");
        tags.put("GENITIVE2", "gen2");
        tags.put("DATIVE", "dat");
        tags.put("ACCUSATIVE", "acc");
        tags.put("ACCUSATIVE2", "acc2");
        tags.put("ABLTIVE", "ins");
        tags.put("PREPOSITIONA", "loc");
        tags.put("PREPOSITIONA1", "loc");
        tags.put("PREPOSITIONA2", "loc2");
        tags.put("VOATIVE", "voc");
        tags.put("PERFECT", "pf");
        tags.put("IMPERFECT", "ipf");
        tags.put("TRAN", "tran");
        tags.put("INTR", "intr");
        tags.put("PER1", "1p");
        tags.put("PER2", "2p");
        tags.put("PER3", "3p");
        tags.put("PRESENT", "praes");
        tags.put("PAST", "praet");
        tags.put("FUTURE", "fut");
        tags.put("INDICATIVE", "indic");
        tags.put("IMPERATIVE", "imper,imper2");
        tags.put("ACTIVE", "act");
        tags.put("PASSIVE", "pass");
        tags.put("SUPR", "supr");
        tags.put("CMP2", "comp2");
        tags.put("COUN", "adnum");
        tags.put("PRNT", "PARENTH");
        tags.put("17", "S");
        tags.put("18", "A,plen");
        tags.put("19", "A,brev");
        tags.put("20", "V");
        tags.put("21", "V,inf");
        tags.put("22", "V,partcp,plen");
        tags.put("23", "V,partcp,brev");
        tags.put("24", "V,ger");
        tags.put("25", "V,ger,ipf");
        tags.put("28", "NUM");
        tags.put("9", "ADV");
        tags.put("11", "PRAEDIC");
        tags.put("14", "PART");
        tags.put("12", "PR");
        tags.put("15", "INTJ");
        tags.put("13", "CONJ");
        tags.put("30", "S");
        tags.put("31", "PART");
        tags.put("10", "A,comp");
        tags.put("REFL", "med");
    }
}
