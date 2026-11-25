# Save: contexto del proyecto

Este documento explica para qué existe la app, qué cambió en cada versión y por qué, y qué
se propone hacer después. Sirve como memoria del proyecto: si en el futuro alguien (tú, otra
persona o una IA) retoma el trabajo, aquí encuentra el porqué de cada decisión.

---

## 1. Por qué existe Save

La idea original fue tener **control absoluto del dinero** con una app sencilla, instalable
en Android, que funcione **como una calculadora de presupuesto mensual**: estimas cuánto
entra y cuánto sale, vas sumando y restando conforme pasan las cosas, y en todo momento sabes
**dónde está tu dinero**: en qué banco, en efectivo o en tus fondos de ahorro.

Requisitos que se plantearon desde el inicio:

| Requisito | Cómo se resolvió |
|---|---|
| APK instalable | App Android nativa (Kotlin + Jetpack Compose), compilada por GitHub Actions |
| Ingresos y gastos, fijos y variables | Cada movimiento es fijo (se repite cada mes) o variable (solo su mes) |
| Fondos de ahorro y emergencia | Pestaña de fondos con meta y barra de progreso |
| Flujo mes a mes | Flujo = ingresos − gastos − ahorro, por mes |
| Banco o efectivo | Cada movimiento dice de qué cuenta sale o a cuál entra |
| Diseño minimalista y fácil de entender | Blanco y negro, color solo para dar significado (verde, rojo, azul) |
| Que se pueda actualizar | La app busca nuevas *releases* en GitHub y se instala encima |

### La fórmula central

```
Flujo del mes  = Ingresos (fijos + variables) − Gastos (fijos + variables) − Ahorro
Saldo final    = Saldo inicial + Flujo del mes
Saldo inicial del mes siguiente = Saldo final de este mes
```

- **Ahorro** = lo que mandaste a fondos menos lo que retiraste de ellos. Se resta del flujo
  porque sale de tus cuentas, pero no se pierde: se sigue viendo en el total.
- **Real vs. estimado:** los fijos aparecen como *pendientes* hasta que los marcas con ✓.
  Lo real solo cuenta lo que ya pasó; lo estimado incluye los pendientes y muestra cómo
  cerrarías el mes.

---

## 2. Historial de versiones

### v1.0.0: la base
**Por qué:** construir la primera versión funcional con todo lo pedido al inicio.

- Pantalla del **mes** con flechas para cambiar de mes, el **flujo** en grande, y ingresos,
  gastos y ahorro, separados en fijos y variables.
- **Captura con teclado de calculadora** (acepta `1200+350`, `3×150`, etc.).
- Tipos de movimiento: **Ingreso**, **Gasto**, **Fondo** (aportar o retirar) y **Mover** (pasar
  dinero de banco a efectivo y al revés, por ejemplo un retiro del cajero).
- **Fijos con confirmación ✓**: aparecen cada mes como pendientes hasta que se pagan o se
  reciben. Al eliminarlos puedes quitarlos "desde este mes" sin perder el historial.
- **Presupuesto mensual**: un límite de gastos con barra de "te quedan $X". Se hereda a los
  meses siguientes.
- **Fondos** de ahorro y de emergencia con meta.
- **Respaldo**: exportar e importar todos los datos en un archivo JSON.
- **Actualizaciones dentro de la app**: busca la última release en GitHub, descarga el APK
  y abre el instalador.
- Todas las versiones se firman con **la misma llave**, para que cada actualización se instale
  encima de la anterior sin borrar datos.
- Los datos se guardan **solo en el teléfono**.

**Problema resuelto en el camino:** el repositorio era privado y GitHub no deja ver las
releases de un repositorio privado sin autenticarse, así que se hizo público para que las
actualizaciones automáticas funcionaran.

---

### v1.1.0: etiquetas, varias cuentas y saldo que se arrastra
**Por qué:** tres pedidos.

1. *"Poder ponerle etiqueta a cada gasto para hacer gráficos y ver el porcentaje."*
2. *"Tengo BBVA, Revolut, Stori… quiero saber cuánto hay en cada banco o en efectivo."*
3. *"Lo que sobra del mes debe pasar al saldo inicial del siguiente."*

Cambios:
- **Etiquetas** para ingresos y gastos (Comida, Transporte, Vivienda, Servicios, Salud,
  Entretenimiento, Compras, Otros, Sueldo, Extra). Se crean desde la captura (**+ Etiqueta**)
  y se renombran o eliminan en Ajustes.
- **Gráfica "En qué se va tu dinero" / "De dónde viene"**: una barra con el reparto y una
  tabla con monto y porcentaje por etiqueta. Usa una paleta de 8 colores verificada para
  personas con daltonismo; cada color va siempre acompañado de su nombre y porcentaje.
- **Cuentas propias**: la pestaña *Fondos* pasa a llamarse **Cuentas**. Ahí se agregan BBVA,
  Revolut, Stori, Efectivo… Cada cuenta es de tipo banco o efectivo y tiene su saldo inicial.
  *Mover* permite elegir la cuenta de origen y la de destino.
- **Saldo inicial + Flujo = Saldo final**, visible en el mes. El saldo final pasa como
  saldo inicial del mes siguiente.
- **Migración automática**: los datos de la v1.0.0 se convirtieron solos a cuentas
  "Banco" y "Efectivo", con sus saldos iniciales.

---

### v1.2.0: fondos más realistas
**Por qué:** dos casos reales que no cabían.

1. *"Ya tenía dinero ahorrado y no quiero que me descuente de Banco ni ensucie el flujo."*
2. *"Los rendimientos diarios de Revolut, o un reembolso, caen directo al fondo sin pasar
   por BBVA."*

Cambios:
- **Saldo inicial por fondo** ("Ya tenía ahorrado"): es el punto de partida del fondo y no
  afecta cuentas ni flujo.
- **Aportar** ahora tiene dos orígenes:
  - **Desde una cuenta**: traspaso. Sale de BBVA (por ejemplo) y entra al fondo.
  - **Ingreso directo**: dinero que entra directo al fondo. Cuenta como **ingreso y como
    ahorro** a la vez: aparece en tus ingresos y en la gráfica, pero **no cambia el flujo**
    ni el saldo final, porque no llegó a ninguna de tus cuentas.
- Nueva etiqueta de ingreso **Rendimientos**.

---

### v1.3.0: fechas de pago, recordatorios y efectivo real
**Por qué:** dos ideas.

1. *"Que las suscripciones y los ingresos programados tengan fecha, y que ese día me llegue
   una notificación preguntando si ya hice el pago."*
2. *"Si anoto $100 en efectivo y gasto $50, que se reste solo y sepa el efectivo real que
   traigo hasta que lo deposite."*

Cambios:
- **Día de pago** para cada fijo, elegido en un calendario del 1 al 31. Si eliges el 31, en
  meses más cortos se usa el último día. Los variables también tienen día.
- **Notificaciones** el día de pago:
  - 9:00 → *"¿Ya pagaste Netflix? $199 · vence hoy"*;
  - 19:00 → otro aviso, si sigue pendiente;
  - botón **Ya lo pagué** (o **Ya lo recibí**, si es un ingreso), que lo marca sin abrir la app;
  - **resumen diario de vencidos** cada mañana;
  - opción de **avisar un día antes** (en Ajustes → Recordatorios).
- En el mes, cada fijo muestra *"vence el 15"*, *"vence hoy"* o *"vencido el 15"* (estos dos
  últimos en rojo).
- **Efectivo real**: ya se restaba solo, pero ahora se ve al capturar. Cada cuenta muestra su
  saldo en el botón para elegirla (*Efectivo · $50*) y la app avisa si un gasto la dejaría en
  negativo (*"Te quedarían −$20 en Efectivo"*).

**Notas:** los fijos que ya existían quedaron con el día en que se crearon como fecha de
pago; conviene revisarlos. Android puede retrasar un poco las notificaciones por ahorro de
batería.

---

## 3. Propuestas para siguientes versiones

Ordenadas de más a menos útil, según lo que se ha ido viendo:

### Prioridad alta
1. **Movimientos únicos programados a futuro.** Hoy un pago o ingreso de una sola vez
   cuenta en cuanto se registra. La propuesta es que quede *pendiente* hasta su fecha, con
   recordatorio igual que los fijos (por ejemplo, un pago de tarjeta el día 15 o un bono
   que llega en diciembre).
2. **Retiro de fondo directo a un gasto.** El caso contrario al ingreso directo: pagar algo
   directamente desde el ahorro (por ejemplo, con la tarjeta de Revolut) sin pasar por una
   cuenta.
3. **Gráfica de tendencia mes a mes.** Ingresos, gastos y saldo final de los últimos 6 o 12
   meses, para ver si vas mejorando.
4. **Alarmas exactas para los recordatorios**, si en el teléfono llegan muy tarde
   (exactamente a las 9:00 y 19:00, en lugar de la revisión aproximada de cada hora).

### Prioridad media
5. **Tarjetas de crédito.** Una cuenta tipo "crédito" con fecha de corte y fecha de pago,
   donde los gastos se acumulan como deuda y el pago de la tarjeta sale del banco.
6. **Presupuesto por etiqueta**, por ejemplo "Comida: máximo $3,000 al mes", con aviso al
   acercarse al límite.
7. **Buscar y filtrar movimientos** por etiqueta, cuenta o texto.
8. **Respaldo automático**: exportar una copia periódica a una carpeta o a Google Drive, para
   no depender de hacerlo a mano.

### Prioridad baja / ideas
9. **Varias monedas** (pesos y dólares, por ejemplo para Revolut) con tipo de cambio manual.
10. **Widget** en la pantalla de inicio con el saldo disponible y lo pendiente del mes.
11. **Bloqueo con huella o PIN** al abrir la app.
12. **Exportar a Excel/CSV** para analizar los datos fuera de la app.

---

## 4. Cómo se publica una versión

1. Los cambios se desarrollan en una rama y se unen a `main` con un *pull request*.
2. En GitHub: **Actions → Android → Run workflow** → escribir la versión (ej. `1.4.0`).
   También se puede publicar con una etiqueta: `git tag v1.4.0 && git push origin v1.4.0`.
3. GitHub Actions corre las pruebas, compila el APK firmado y crea la release.
4. La app detecta la versión nueva al abrirse; también se puede buscar en
   **Ajustes → Buscar actualizaciones**.

El número de versión siempre debe ser mayor que el anterior.

---

## 5. Datos técnicos (resumen)

- **Plataforma:** Android 8.0 o superior (minSdk 26), Kotlin + Jetpack Compose (Material 3).
- **Almacenamiento:** un archivo JSON local en el teléfono. El formato tiene versión
  (actualmente **v3**) y se migra solo al actualizar la app.
- **Montos:** se guardan en centavos para evitar errores de redondeo.
- **Lógica de cálculo:** `app/src/main/java/com/save/finanzas/domain/`, con pruebas
  automáticas en `app/src/test/` (9 pruebas: flujo, saldos, cuentas, etiquetas, fondos,
  migraciones, fechas de pago, recordatorios y versiones).
- **Recordatorios:** una tarea en segundo plano (WorkManager) revisa cada hora qué fijos
  vencen.
- **Firma:** `keystore/save.jks`. Si se cambia, hay que desinstalar la app, así que antes hay
  que exportar un respaldo. Como el repositorio es público, la llave también lo es.
