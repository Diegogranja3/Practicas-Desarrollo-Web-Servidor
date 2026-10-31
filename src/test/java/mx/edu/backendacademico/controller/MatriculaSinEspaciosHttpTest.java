package mx.edu.backendacademico.controller;

// Reto U06: la frontera HTTP rechaza espacios internos en la matrícula.

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatriculaSinEspaciosHttpTest {
    private static final String ERROR = "\"campo\":\"matricula\",\"mensaje\":\"no debe contener espacios internos\"";
    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int puerto;

    @Test void rechazaEspacioInterno() throws Exception {
        var r = crear("A 001");
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains("\"codigo\":\"DATOS_INVALIDOS\""), r.body());
        assertTrue(r.body().contains(ERROR), r.body());
    }

    @Test void rechazaTabuladorInterno() throws Exception {
        var r = crear("A\\t002");  // \t dentro del JSON
        assertEquals(400, r.statusCode());
        assertTrue(r.body().contains(ERROR), r.body());
    }

    @Test void conservaLaNormalizacionDeEspaciosExternos() throws Exception {
        var r = crear("  a003  ");
        // Los espacios externos y las minúsculas se aceptan; el dominio guarda la forma canónica.
        assertEquals(201, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"matricula\":\"A003\""), r.body());
    }

    @Test void vaciaSoloReportaNotBlank() throws Exception {
        var r = crear("");
        assertEquals(400, r.statusCode());
        assertFalse(r.body().contains(ERROR), "El patrón no debe duplicar el error de @NotBlank: " + r.body());
        assertTrue(r.body().contains("\"campo\":\"matricula\""), r.body());
    }

    private HttpResponse<String> crear(String matricula) throws Exception {
        String json = "{\"matricula\":\"" + matricula + "\",\"nombre\":\"Ada Lovelace\",\"correo\":\"ada@universidad.mx\"}";
        var peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/alumnos"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
