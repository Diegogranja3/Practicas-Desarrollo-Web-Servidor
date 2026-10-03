# Reto U02 — Parámetro opcional `nombre`

Rama: `reto-u02-nombre`. `master` conserva la solución base (`{"mensaje":"Hola backend"}`).

## Cambio

`SaludoController.saludar` recibe `@RequestParam(defaultValue = "Mundo") String nombre` y arma el
saludo en Java: `Map.of("mensaje", "Hola " + nombre)`. La ruta y el método HTTP no cambian.

## Peticiones conservadas (`requests.http`)

| Petición | Status | Cuerpo |
|---|---|---|
| `GET /api/v1/saludo` | 200 | `{"mensaje":"Hola Mundo"}` |
| `GET /api/v1/saludo?nombre=Ada` | 200 | `{"mensaje":"Hola Ada"}` |

Evidencia: `evidencia/reto-peticiones-http.txt`.

## ¿Dónde se resuelve el parámetro y dónde se serializa?

1. **Resolución (antes de invocar el método).** Tomcat recibe la petición y la entrega al
   `DispatcherServlet`. El `RequestMappingHandlerMapping` elige `saludar` por la ruta `/api/v1/saludo` y el
   método GET. Antes de llamarlo, el `RequestParamMethodArgumentResolver` lee `nombre` de la query string
   (`?nombre=Ada`). Si no viene, usa `defaultValue = "Mundo"`. Como hay `defaultValue`, el parámetro
   deja de ser obligatorio y nunca llega `null`.
2. **Ejecución.** El método solo trabaja con objetos Java: concatena el texto y devuelve un `Map`.
   No sabe nada de JSON ni de HTTP.
3. **Serialización (después de que el método regresa).** Como la clase es `@RestController`, el valor de retorno
   va al cuerpo de la respuesta. El `RequestResponseBodyMethodProcessor` negocia el formato con
   `Accept: application/json` y elige el convertidor de Jackson, que escribe `{"mensaje":"Hola Ada"}` con
   `Content-Type: application/json`.

En resumen, el parámetro se resuelve a la entrada del método y el JSON se produce a su salida. Esa
frontera permite cambiar el texto del saludo sin tocar la ruta, el método HTTP ni el formato.
