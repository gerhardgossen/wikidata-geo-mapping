package eu.sobigdata.tracking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;

import com.google.common.net.UrlEscapers;

import static com.google.common.net.UrlEscapers.urlFormParameterEscaper;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

public class Mapping {

    private enum Headers {
        TOKEN, ENTITY, OFFSET, ENTITY_URL, CONFIDENCE, WIKIDATA_ID, COORDINATES
    }

    private final static CSVFormat FORMAT = CSVFormat.DEFAULT.withHeader(Headers.class);
    private final static String QUERY = "PREFIX schema: <http://schema.org/>\n"
            + "SELECT DISTINCT ?data ?coordinates ?label\n"
            + "WHERE {\n" + "  ?entity schema:about ?data .\n"
            + "  ?data wdt:P625 ?coordinates; \n"
            + "              rdfs:label ?label .\n"
            + "  VALUES ?entity { $VALUES$ }\n"
            + "FILTER (langMatches(lang(?label), 'de')) \n"
            + "}";
    private final static String URL_PREFIX = "https://de.wikipedia.org/wiki/";
    private final static int BUFFER_SIZE = 10;
    private final Map<String, String> wikiDataIds = new HashMap<>();
    private final Map<String, String> coordinates = new HashMap<>();
    private final Set<String> bufferedEntities = new HashSet<>();
    private final List<CSVRecord> bufferedRecords = new ArrayList<>(BUFFER_SIZE);
    private final HttpClient client = HttpClientBuilder.create()
        .setUserAgent("SoBigData Entity Resolver <gossen@l3s.de>")
        .disableCookieManagement()
        .build();

    public static void main(String[] args) throws IOException {
        Mapping mapping = new Mapping();
        for (String filename : args) {
            File inputFile = new File(filename);
            File outputFile = new File(inputFile.getParentFile(), inputFile.getName().replaceFirst("\\.csv", "-geo.csv"));
            mapping.parse(filename, inputFile, outputFile);
        }
    }

    private void parse(String filename, File file, File outputFile) throws IOException {
        CSVParser parser = CSVParser.parse(file, UTF_8, FORMAT);

        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outputFile), UTF_8);
        CSVPrinter printer = new CSVPrinter(out, FORMAT.withHeader((Class<Enum<?>>) null));
        for (CSVRecord record : parser) {
            if (coordinates.containsKey(record.get(Headers.ENTITY))) {
                emit(printer, record);
            } else {
                query(printer, record);
            }
        }
        clearBuffer(printer);

    }

    private void query(CSVPrinter printer, CSVRecord record) throws IOException {
        String entity = record.get(Headers.ENTITY);
        bufferedEntities.add(entity);
        bufferedRecords.add(record);

        if (bufferedEntities.size() >= BUFFER_SIZE) {
            clearBuffer(printer);
        }

    }

    private void clearBuffer(CSVPrinter printer) throws IOException {
        if (bufferedEntities.isEmpty()) {
            return;
        }
        String values = bufferedEntities.stream()
            .map(UrlEscapers.urlPathSegmentEscaper()::escape)
            .map(e -> String.format("<%s%s>", URL_PREFIX, e))
            .collect(joining(" "));
        String query = QUERY.replaceFirst("\\$VALUES\\$", values);
        HttpUriRequest request = new HttpGet(
            "https://query.wikidata.org/sparql?query=" + urlFormParameterEscaper().escape(query));
        request.addHeader(HttpHeaders.ACCEPT, "text/csv");
        HttpResponse response = client.execute(request);
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new RuntimeException("API returned " + response.getStatusLine());
        }
        try (InputStreamReader content = new InputStreamReader(response.getEntity().getContent())) {
            CSVParser responseParser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(content);
            for (CSVRecord record : responseParser) {
                String entity = record.get("label");
                String coordinate = record.get("coordinates");
                String wikidataId = record.get("data");
                if (bufferedEntities.contains(entity)) {
                    wikiDataIds.put(entity, wikidataId);
                    coordinates.put(entity, coordinate);
                    bufferedEntities.remove(entity);
                }
            }
            for (String remainingEntity : bufferedEntities) {
                wikiDataIds.put(remainingEntity, "");
                coordinates.put(remainingEntity, "");
            }
        }
        bufferedEntities.clear();
        bufferedRecords.clear();

        for (CSVRecord bufferedRecord : bufferedRecords) {
            emit(printer, bufferedRecord);
        }
    }

    private void emit(CSVPrinter printer, CSVRecord record) throws IOException {
        String token = record.get(Headers.TOKEN);
        String entity = record.get(Headers.ENTITY);
        String offset = record.get(Headers.OFFSET);
        String entityUrl = record.get(Headers.ENTITY_URL);
        String confidence = record.get(Headers.CONFIDENCE);
        String wikidataId = wikiDataIds.get(entity);
        String coordinate = coordinates.get(entity);
        printer.printRecord(token, entity, offset, entityUrl, confidence, wikidataId, coordinate);
    }

}
