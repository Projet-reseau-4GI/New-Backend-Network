package Projects.Network.service;

import Projects.Network.dto.DocumentAnalysisResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import Projects.Network.repository.DocumentRepository;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DocumentAnalysisServiceTest {

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private EnhancedDocumentService enhancedDocumentService;

    private final DocumentAnalysisService analysisService = new DocumentAnalysisService(null, null);

    @Test
    public void testAnalyzeCNI() {
        String ocrText = "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_75_41_339_350.jpg\" alt=\"Image\" width=\"72%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "NOM / SURNAME\n" +
                "\n" +
                "AHMED JALIL\n" +
                "\n" +
                "PRÉNOMS / GIVEN NAMES\n" +
                "\n" +
                "TADIDA DJIDERE\n" +
                "\n" +
                "100508038\n" +
                "\n" +
                "\n" +
                "\n" +
                "DATE DE NAISSANCE / DATE OF BIRTH\n" +
                "\n" +
                "31.03.2006\n" +
                "\n" +
                "DATE D'EXPIRATION / DATE OF EXPIRY\n" +
                "\n" +
                "23.07.2035\n" +
                "\n" +
                "SIGNATURE / HOLDER'S SIGNATURE\n" +
                "\n" +
                "SEXE / SEX\n" +
                "\n" +
                "M\n" +
                "\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_20_13_205_223.jpg\" alt=\"Image\" width=\"48%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "NOM DU PÈRE / FATHER'S NAME\n" +
                "\n" +
                "ARABO DJIDERE\n" +
                "\n" +
                "NOM DE LA MÈRE / MOTHER'S NAME\n" +
                "\n" +
                "LADO TADIDA CLARISSSE\n" +
                "\n" +
                "LIEU DE NAISSANCE / PLACE OF BIRTH\n" +
                "\n" +
                "MBOUDA\n" +
                "\n" +
                "PROFESSION / OCCUPATION\n" +
                "\n" +
                "ETUDIANT-E-\n" +
                "\n" +
                "DATE DE DÉLIVRANCE / TAILLE /\n" +
                "\n" +
                "DATE OF ISSUE HEIGHT\n" +
                "\n" +
                "23.07.2025 1.83 m\n" +
                "\n" +
                "NUMÉRO CNI / NIC NUMBER\n" +
                "\n" +
                "AA12678606\n" +
                "\n" +
                "LE DGSN / THE DGNS\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_17_537_187_592.jpg\" alt=\"Image\" width=\"44%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "Martin MBARGA NGUÉLÉ";

        DocumentAnalysisResponse response = analysisService.analyze(ocrText, "");

        assertEquals("ID_CARD", response.getDocumentType());
        assertEquals("AA12678606", response.getDocumentNumber());
        assertEquals("AHMED JALIL TADIDA DJIDERE", response.getHolderName());
        assertEquals("2006-03-31", response.getDateOfBirth().toString());
        assertEquals("2025-07-23", response.getIssueDate().toString());
        assertEquals("2035-07-23", response.getExpirationDate().toString());
        assertEquals("ARABO DJIDERE", response.getAdditionalFields().get("fatherName"));
        assertEquals("LADO TADIDA CLARISSSE", response.getAdditionalFields().get("motherName"));
        assertEquals("MBOUDA", response.getAdditionalFields().get("placeOfBirth"));
        assertEquals("1.83 m", response.getAdditionalFields().get("height"));
    }

    @Test
    public void testAnalyzeDriverLicense() {
        String ocrText = "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_26_9_114_101.jpg\" alt=\"Image\" width=\"10%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "## Permis de conduire Driving Licence\n" +
                "\n" +
                "## République du Cameroun · Republic of Cameroon\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_50_128_271_411.jpg\" alt=\"Image\" width=\"27%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "1. ATCHINE OUDAM\n" +
                "\n" +
                "2. HANNIEL\n" +
                "\n" +
                "3. 11-01-2005, YACUNDE\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_594_127_780_236.jpg\" alt=\"Image\" width=\"22%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "4a. 22-05-2024 4c. DIME I. B.\n" +
                "\n" +
                "4b. 22-05-2034 4d. CE-639-0324-2\n" +
                "\n" +
                "\n" +
                "\n" +
                "5. CE-110760-24\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_319_337_432_394.jpg\" alt=\"Image\" width=\"13%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "9. B";

        DocumentAnalysisResponse response = analysisService.analyze(ocrText, "");

        assertEquals("DRIVER_LICENSE", response.getDocumentType());
        assertEquals("CE-110760-24", response.getDocumentNumber());
        assertEquals("ATCHINE OUDAM HANNIEL", response.getHolderName());
        assertEquals("2005-01-11", response.getDateOfBirth().toString());
        assertEquals("2024-05-22", response.getIssueDate().toString());
        assertEquals("2034-05-22", response.getExpirationDate().toString());
        assertEquals("DIME I. B.", response.getAdditionalFields().get("authority"));
        assertEquals("CE-639-0324-2", response.getAdditionalFields().get("reference"));
        assertEquals("B", response.getAdditionalFields().get("categories"));
    }

    @Test
    public void testAnalyzePassport() {
        String ocrText = "Signature du titulaire Bearer's signature\n" +
                "\n" +
                "Martin MBARGA NGUELE\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_78_519_156_576.jpg\" alt=\"Image\" width=\"9%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "## Passeport Passport\n" +
                "\n" +
                "## REPUBLIQUE DU CAMEROUN / REPUBLIC OF CAMEROON\n" +
                "\n" +
                "Type / Type\n" +
                "\n" +
                "PP\n" +
                "\n" +
                "Code du pays / Country code\n" +
                "\n" +
                "CMR\n" +
                "\n" +
                "No de passeport / Passport no.\n" +
                "\n" +
                "AB301964\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_615_526_665_560.jpg\" alt=\"Image\" width=\"6%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "1. Nom / Surname\n" +
                "\n" +
                "<div style=\"text-align: center;\"><img src=\"imgs/img_in_image_box_89_628_207_825.jpg\" alt=\"Image\" width=\"14%\" /></div>\n"
                +
                "\n" +
                "\n" +
                "NJEMPOU YAMPEN\n" +
                "\n" +
                "2. Prénoms / Given names\n" +
                "\n" +
                "RACHIDA ROUCHDA\n" +
                "\n" +
                "3. Nationalité / Nationality\n" +
                "\n" +
                "CAMEROUNAISE/ CAMEROONIAN\n" +
                "\n" +
                "4. Date de naissance / Date of birth\n" +
                "\n" +
                "31.03.2006\n" +
                "\n" +
                "5. Sexe / Sex\n" +
                "\n" +
                "F\n" +
                "\n" +
                "6. Lieu de naissance / Place of birth\n" +
                "\n" +
                "DOUALA\n" +
                "\n" +
                "7. Date de délivrance / Date of issue  \n" +
                "\n" +
                "18.09.2025  \n" +
                "\n" +
                "8. Date d'expiration / Date of expiry  \n" +
                "\n" +
                "18.09.2030\n" +
                "\n" +
                "9. Profession / Occupation\n" +
                "\n" +
                "ETUDIANTE\n" +
                "\n" +
                "10. Taille / Height 11. CAN\n" +
                "\n" +
                "1.67 m 848484\n" +
                "\n" +
                "12. Lieu de délivrance / Place of issue\n" +
                "\n" +
                "YAOUNDE\n" +
                "\n" +
                "13. Signature du titulaire / Bearer's signature\n" +
                "\n" +
                "Aamp\n" +
                "\n" +
                "PPCMRNJEMPOU<YAMPEN<<RACHIDA<ROUCHDA<<<<<<<AB301964<2CMRO603319F3009185<<<<<<<<<<<06";

        DocumentAnalysisResponse response = analysisService.analyze(ocrText, "");

        assertEquals("PASSPORT", response.getDocumentType());
        assertEquals("AB301964", response.getDocumentNumber());
        assertEquals("NJEMPOU YAMPEN RACHIDA ROUCHDA", response.getHolderName());
        assertEquals("2006-03-31", response.getDateOfBirth().toString());
        assertEquals("2025-09-18", response.getIssueDate().toString());
        assertEquals("2030-09-18", response.getExpirationDate().toString());
    }
}
