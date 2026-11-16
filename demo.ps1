<#
  Reto U12: reproduce todas las observaciones de la entrega sin depender del IDE.
  Requisitos: la aplicación levantada con "docker compose up -d" (ver ENTREGA.md) y una base NUEVA,
  porque el script crea los alumnos A001 y A002.
  Uso:  powershell -ExecutionPolicy Bypass -File .\demo.ps1
#>
$U   = 'http://localhost:8080'
$tmp = Join-Path $env:TEMP ("academico-demo-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
$cookiesDocente = Join-Path $tmp 'docente.txt'
$cookiesAlumno  = Join-Path $tmp 'alumno.txt'

function Json([string]$contenido) {
    # PowerShell 5.1 elimina comillas al pasar argumentos a curl.exe: el cuerpo viaja en un archivo.
    $f = Join-Path $tmp ([guid]::NewGuid().ToString() + '.json')
    [IO.File]::WriteAllText($f, $contenido)
    return "@$f"
}

function Pedir([string]$titulo, [string[]]$argumentos) {
    Write-Output ''
    Write-Output "### $titulo"
    $salida = & curl.exe -s -i --max-time 60 @argumentos
    $salida | Where-Object { $_ -match '^HTTP/' -or $_ -match '^Location:' -or $_ -match '^\s*[\{\[]' }
}

function Token([string]$usuario, [string]$cookies) {
    $json = & curl.exe -s -u $usuario -b $cookies -c $cookies "$U/api/v1/csrf"
    return ($json | ConvertFrom-Json).token
}

function IdDe($lineas) {
    $cuerpo = $lineas | Where-Object { $_ -match '^\s*\{' } | Select-Object -First 1
    return ($cuerpo | ConvertFrom-Json).id
}

Write-Output '=================== 1. Salud ==================='
Pedir 'GET /actuator/health/liveness'  @("$U/actuator/health/liveness")
Pedir 'GET /actuator/health/readiness' @("$U/actuator/health/readiness")

$td = Token 'docente:docente123' $cookiesDocente
$ta = Token 'alumno:alumno123'   $cookiesAlumno

Write-Output ''
Write-Output '=================== 2. Éxito, validación, ausencia y conflicto ==================='
$a1 = Pedir 'Éxito: POST /api/v1/alumnos A001 (docente + CSRF) -> 201' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A001","nombre":"Ada Lovelace","correo":"ada@universidad.mx"}'),"$U/api/v1/alumnos")
$a1
$idA001 = IdDe $a1
Pedir 'Validación: cuerpo inválido -> 400 DATOS_INVALIDOS' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"","nombre":"A","correo":"no-es-correo"}'),"$U/api/v1/alumnos")
Pedir 'Ausencia: GET /api/v1/alumnos/999 -> 404 ALUMNO_NO_ENCONTRADO' @('-u','docente:docente123',"$U/api/v1/alumnos/999")
Pedir 'Conflicto: POST A001 otra vez -> 409 MATRICULA_DUPLICADA' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A001","nombre":"Otra Persona","correo":"otra@universidad.mx"}'),"$U/api/v1/alumnos")

Write-Output ''
Write-Output '=================== 3. Dos rechazos de seguridad de causas distintas ==================='
Pedir 'Autenticación: GET /api/v1/alumnos/1 sin credenciales -> 401' @("$U/api/v1/alumnos/$idA001")
Pedir 'CSRF: POST de docente con credenciales válidas pero sin token -> 403' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A009","nombre":"Sin Token","correo":"sintoken@universidad.mx"}'),"$U/api/v1/alumnos")
Pedir 'Rol: POST de ALUMNO con token CSRF válido -> 403' @('-X','POST','-u','alumno:alumno123','-b',$cookiesAlumno,'-H',"X-CSRF-TOKEN: $ta",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A008","nombre":"Sin Permiso","correo":"sinpermiso@universidad.mx"}'),"$U/api/v1/alumnos")

Write-Output ''
Write-Output '=================== 4. Mejora de acceso por propietario ==================='
$a2 = Pedir 'Preparación: POST A002 (docente)' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A002","nombre":"Emmy Noether","correo":"emmy@universidad.mx"}'),"$U/api/v1/alumnos")
$a2
$idA002 = IdDe $a2
Pedir "Propietario a001 -> su ficha /alumnos/$idA001 -> 200" @('-u','a001:a001pass',"$U/api/v1/alumnos/$idA001")
Pedir "a001 -> ficha ajena /alumnos/$idA002 -> 404 (igual que inexistente)" @('-u','a001:a001pass',"$U/api/v1/alumnos/$idA002")
Pedir 'a001 -> lista completa /alumnos -> 403' @('-u','a001:a001pass',"$U/api/v1/alumnos")
Pedir "Docente -> /alumnos/$idA002 -> 200" @('-u','docente:docente123',"$U/api/v1/alumnos/$idA002")

Write-Output ''
Write-Output '=================== 5. Falla controlada de PostgreSQL ==================='
Write-Output ''
Write-Output 'PS> docker compose stop postgres'
docker compose stop postgres 2>&1 | ForEach-Object { "$_" }
Pedir 'Liveness con PostgreSQL detenido -> 200 UP (el proceso vive)' @("$U/actuator/health/liveness")
Pedir 'Readiness con PostgreSQL detenido -> 503 DOWN (no debe recibir tráfico)' @("$U/actuator/health/readiness")
Pedir "Lectura de A001, que ya está en la caché local -> 200 (la caché responde sin consultar la base)" @('-u','docente:docente123',"$U/api/v1/alumnos/$idA001")
Pedir 'Operación que sí necesita la base (crear A003) -> 500 ERROR_INTERNO con referencia' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A003","nombre":"Grace Hopper","correo":"grace@universidad.mx"}'),"$U/api/v1/alumnos")
Write-Output ''
Write-Output 'PS> docker compose start postgres'
docker compose start postgres 2>&1 | ForEach-Object { "$_" }
$listo = $false
for ($i = 0; $i -lt 30 -and -not $listo; $i++) {
    Start-Sleep -Seconds 2
    $estado = & curl.exe -s -o NUL -w '%{http_code}' "$U/actuator/health/readiness"
    $listo = ($estado -eq '200')
}
Pedir 'Readiness tras recuperar PostgreSQL -> 200 UP' @("$U/actuator/health/readiness")
Pedir 'La creación vuelve a funcionar -> 201' @('-X','POST','-u','docente:docente123','-b',$cookiesDocente,'-H',"X-CSRF-TOKEN: $td",'-H','Content-Type: application/json','--data-binary',(Json '{"matricula":"A003","nombre":"Grace Hopper","correo":"grace@universidad.mx"}'),"$U/api/v1/alumnos")

Remove-Item -Recurse -Force $tmp
