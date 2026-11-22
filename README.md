# Save

App Android minimalista para finanzas personales: un presupuesto mensual tipo calculadora donde
registras lo que entra y lo que sale, y ves en todo momento cuánto tienes en **banco**,
en **efectivo** y en tus **fondos**.

## Qué hace

- **Ingresos y gastos, fijos o variables.** Los fijos (sueldo, renta, suscripciones…) se repiten
  cada mes. Aparecen como *pendientes* hasta que los marcas con ✓, así puedes ver lo estimado
  y lo real por separado.
- **Flujo del mes** = ingresos − gastos − lo que mandaste a fondos. Se muestra el real y el
  estimado al cierre del mes, contando los fijos pendientes.
- **Saldo que se arrastra:** saldo inicial + flujo = saldo final, y ese saldo final es el saldo
  inicial del mes siguiente.
- **Varias cuentas** (BBVA, Revolut, Stori, Efectivo…), cada una de tipo banco o efectivo y con
  su saldo inicial. Cada movimiento indica de qué cuenta sale o a cuál entra. Con *Mover* pasas
  dinero entre cuentas (por ejemplo, un retiro del cajero).
- **Etiquetas** para ingresos y gastos (Comida, Transporte, Sueldo…), con una gráfica del
  porcentaje que representa cada una en el mes.
- **Fondos de ahorro y de emergencia**, con meta opcional, barra de progreso y saldo inicial
  (lo que ya tenías ahorrado; no afecta tus cuentas ni el flujo). Un aporte puede ser un
  traspaso desde una cuenta o un ingreso directo al fondo (rendimientos, reembolsos). El ingreso
  directo cuenta como ingreso y como ahorro, así que no cambia el flujo.
- **Presupuesto mensual:** un límite de gastos con lo que te queda. Aplica al mes en que lo
  defines y a los siguientes.
- **Saldos acumulados** mes a mes, partiendo del saldo inicial que configures.
- **Teclado de calculadora** al capturar montos (`1200+350`, `3×150`…).
- **Respaldo**: exportar e importar todos los datos en un archivo JSON.
- **Actualizaciones dentro de la app:** busca la última release de GitHub, descarga el APK y abre
  el instalador.

Los datos se guardan solo en el teléfono y la app no los envía a ningún lado.

## Publicar una versión nueva

Cada push compila el APK y lo deja como *artifact* en GitHub Actions. Para publicar una
**release** que la app pueda descargar:

- **Desde GitHub:** Actions → *Android* → *Run workflow* → escribe la versión (ej. `1.1.0`).
- **Desde git:** `git tag v1.1.0 && git push origin v1.1.0`.

El número de versión tiene que ser mayor que el anterior, porque así la app detecta que hay una
actualización.

> **Importante:** si el repositorio es privado, la app no puede ver sus releases, ya que la API
> pública de GitHub responde 404. Para que la actualización automática funcione, hay dos opciones:
> hacer público el repositorio, o publicar las releases en un repositorio público aparte y compilar con
> `-PupdateRepo=usuario/repo`.

## Firma

Todas las versiones se firman con la misma llave (`keystore/save.jks`), así que cada APK nuevo
se instala encima del anterior sin borrar tus datos. Si cambias esta llave, vas a tener que
desinstalar la app; antes exporta un respaldo.

## Desarrollo

- Kotlin + Jetpack Compose (Material 3), minSdk 26.
- La lógica de cálculo está en `app/src/main/java/com/save/finanzas/domain` y tiene pruebas en
  `app/src/test`.
- `./gradlew testReleaseUnitTest assembleRelease` (requiere el Android SDK).
