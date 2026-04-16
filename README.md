# Cita Click - Sistema de Gestión de Citas SaaS

Sistema completo de gestión de citas para negocios con modelo de suscripción (Starter, Professional, Enterprise).

> Infraestructura migrada a Digital Ocean Droplet.

## Arquitectura del Sistema

### Stack Tecnológico

**Backend:**
- Java 21
- Spring Boot 3.x
- PostgreSQL (Google Cloud SQL)
- JWT para autenticación
- Flyway para migraciones de base de datos

**Frontend:**
- Vue 3 con Composition API
- Vite como build tool
- Pinia para state management
- Tailwind CSS
- Axios para HTTP requests

**Infraestructura:**
- Google Cloud Platform (Cloud SQL para PostgreSQL)
- Google Cloud Storage (almacenamiento de logos e imágenes)
- Despliegue: Google Cloud Run

### Integraciones Externas

1. **Stripe** - Procesamiento de pagos y suscripciones
2. **Twilio** - SMS y WhatsApp (Professional y Enterprise)
3. **SendGrid** - Email transaccional (Professional y Enterprise)
4. **Google OAuth** - Autenticación con Google

## Modelo de Datos Principal

### Entidades Core

#### Negocio
- Entidad principal que representa un negocio
- Contiene: nombre, plan activo, configuración de horarios, días libres
- Relación con: Usuarios, Clientes, Servicios, Citas

#### Usuario
- Empleados del negocio con acceso al sistema
- Tipos: Admin (dueño), Usuario (empleado)
- Límites por plan: Starter (1), Professional (3), Enterprise (10)

#### Cliente
- Clientes del negocio
- Información de contacto, notas, historial
- Límites por plan: Starter (100), Professional (500), Enterprise (ilimitado)

#### Servicio
- Servicios ofrecidos por el negocio
- Contiene: nombre, duración, precio, activo/inactivo
- Límites por plan: Starter (10), Professional (30), Enterprise (ilimitado)

#### Cita
- Reservas de servicios
- Estados: PENDIENTE, CONFIRMADA, COMPLETADA, CANCELADA
- Soporte para citas recurrentes
- **Relación con múltiples servicios** (un registro por servicio)
- Límites por mes: Starter (50), Professional (200), Enterprise (ilimitado)

#### HorarioTrabajo
- Define días y horas laborales del negocio
- Por día de semana con hora inicio/fin

#### DiaLibre
- Días específicos donde el negocio no opera
- Útil para vacaciones, días festivos

## Endpoints de API

### Base URL
```
/api
```

### Autenticación (`/auth`)

```
POST   /auth/register           - Registro de nuevo negocio
POST   /auth/login              - Login con email/password
POST   /auth/google             - Login con Google OAuth
POST   /auth/refresh            - Refrescar token JWT
POST   /auth/verify-email       - Verificar email
```

### Negocios (`/negocios`)

```
GET    /negocios                - Obtener info del negocio autenticado
PUT    /negocios                - Actualizar información del negocio
PUT    /negocios/horarios       - Actualizar horarios de trabajo
POST   /negocios/dias-libres    - Agregar día libre
DELETE /negocios/dias-libres/{id} - Eliminar día libre
```

### Usuarios (`/usuarios`)

```
GET    /usuarios                - Listar usuarios del negocio
POST   /usuarios                - Crear nuevo usuario (validación de límite por plan)
PUT    /usuarios/{id}           - Actualizar usuario
DELETE /usuarios/{id}           - Eliminar usuario
```

**Restricción:** Requiere plan Professional o superior

### Clientes (`/clientes`)

```
GET    /clientes                - Listar clientes (con búsqueda opcional)
GET    /clientes/{id}           - Obtener cliente específico
GET    /clientes/{id}/perfil360 - Perfil completo con estadísticas
POST   /clientes                - Crear cliente (validación de límite por plan)
PUT    /clientes/{id}           - Actualizar cliente
DELETE /clientes/{id}           - Eliminar cliente
```

### Servicios (`/servicios`)

```
GET    /servicios               - Listar servicios
GET    /servicios/{id}          - Obtener servicio específico
POST   /servicios               - Crear servicio (validación de límite por plan)
PUT    /servicios/{id}          - Actualizar servicio
DELETE /servicios/{id}          - Eliminar servicio
```

### Citas (`/citas`)

```
GET    /citas                   - Listar citas del negocio
GET    /citas/{id}              - Obtener cita específica
POST   /citas                   - Crear cita (validación de límite mensual)
POST   /citas/multiple-servicios - Crear cita con múltiples servicios
PUT    /citas/{id}              - Actualizar cita
PUT    /citas/{id}/estado       - Cambiar estado de cita
DELETE /citas/{id}              - Cancelar cita
GET    /citas/disponibilidad    - Verificar disponibilidad de horario
```

### Planes y Suscripciones (`/planes`)

```
GET    /planes/info             - Información del plan actual
GET    /planes/uso              - Uso actual de recursos vs límites
POST   /planes/upgrade          - Solicitar upgrade de plan
POST   /planes/downgrade        - Solicitar downgrade de plan
```

### Stripe (`/stripe`)

```
POST   /stripe/create-checkout-session  - Crear sesión de pago
POST   /stripe/create-portal-session    - Portal de gestión de suscripción
POST   /stripe/webhook                  - Webhook para eventos de Stripe
```

### Reportes (`/reportes`)

```
GET    /reportes/citas          - Reporte de citas por período
GET    /reportes/ingresos       - Reporte de ingresos
GET    /reportes/clientes       - Reporte de clientes
GET    /reportes/export/pdf     - Exportar reporte en PDF
GET    /reportes/export/excel   - Exportar reporte en Excel
```

### API Keys (`/api-keys`)

```
GET    /api-keys                - Listar API keys
POST   /api-keys                - Crear nueva API key
DELETE /api-keys/{id}           - Revocar API key
```

**Restricción:** Solo disponible en plan Enterprise

### White Label (`/white-label`)

```
GET    /white-label             - Obtener configuración de marca
PUT    /white-label             - Actualizar configuración de marca
```

**Restricción:** Solo disponible en plan Enterprise

### API Externa (v1) - Acceso con API Key

```
GET    /api/v1/clientes         - Listar clientes (requiere API key)
POST   /api/v1/clientes         - Crear cliente (requiere API key)
GET    /api/v1/citas            - Listar citas (requiere API key)
POST   /api/v1/citas            - Crear cita (requiere API key)
```

## Flujos Principales

### 1. Registro de Nuevo Negocio

1. Usuario completa formulario de registro
2. Backend crea:
   - Negocio con plan STARTER y 7 días de prueba
   - Usuario admin asociado
   - Horarios de trabajo por defecto (Lun-Vie 9-18h)
3. Se envía email de verificación
4. Usuario verifica email y accede al dashboard

### 2. Creación de Cita

1. Verificar límite de citas del mes según plan
2. Validar que el cliente pertenece al negocio
3. Validar que el/los servicio(s) pertenecen al negocio y están activos
4. Verificar disponibilidad de horario:
   - Dentro de horario de trabajo
   - No en día libre
   - No traslapa con otra cita
5. Crear registros de cita (uno por servicio si son múltiples)
6. Calcular precio total sumando todos los servicios
7. Si es cita recurrente, generar ocurrencias futuras

### 3. Procesamiento de Pago (Stripe)

1. Usuario selecciona plan en frontend
2. Backend crea Checkout Session en Stripe
3. Usuario completa pago en Stripe
4. Stripe envía webhook al backend
5. Backend actualiza:
   - Plan del negocio
   - Fecha de vencimiento
   - Estado de suscripción
   - Subscription ID de Stripe

### 4. Envío de Recordatorios

**Scheduler diario (00:00):**
1. Buscar citas del día siguiente
2. Filtrar según plan:
   - Starter: No envía recordatorios
   - Professional/Enterprise: Envía recordatorios
3. Validar límite mensual de recordatorios (Professional: 200)
4. Enviar según preferencia del negocio:
   - WhatsApp (Twilio)
   - SMS (Twilio)
   - Email (SendGrid)
5. Marcar cita como `recordatorioEnviado = true`

### 5. Gestión de Usuarios

1. Admin crea nuevo usuario
2. Validar límite según plan:
   - Starter: 1 usuario (solo admin)
   - Professional: 3 usuarios
   - Enterprise: 10 usuarios
3. Crear usuario con rol USUARIO
4. Enviar credenciales por email
5. Usuario puede acceder con sus credenciales

## Planes y Restricciones

### Starter (Prueba 7 días gratis)
- **Citas:** 50/mes
- **Clientes:** 100 máximo
- **Servicios:** 10 máximo
- **Usuarios:** 1 (solo admin)
- **Recordatorios:** No disponibles
- **API Keys:** No disponibles
- **White Label:** No disponible

### Professional ($29/mes)
- **Citas:** 200/mes
- **Clientes:** 500 máximo
- **Servicios:** 30 máximo
- **Usuarios:** 3 máximo
- **Recordatorios:** 200/mes (WhatsApp, SMS, Email)
- **API Keys:** No disponibles
- **White Label:** No disponible

### Enterprise ($99/mes)
- **Citas:** Ilimitadas
- **Clientes:** Ilimitados
- **Servicios:** Ilimitados
- **Usuarios:** 10 máximo
- **Recordatorios:** Ilimitados (WhatsApp, SMS, Email)
- **API Keys:** Disponibles
- **White Label:** Disponible

## Notificaciones de Pago

### Durante Periodo de Prueba
- **Día 6 de 7:** Banner amarillo "Quedan 1 día de prueba. Agrega un método de pago."

### Suscripción Activa
- **5 días antes del vencimiento:** Banner naranja "Tu pago se procesará en 5 días. Verifica tu método de pago."
- **1 día antes:** Banner rojo "Tu suscripción vence mañana."

## Citas con Múltiples Servicios

### Modelo de Datos
- Una cita puede tener múltiples servicios
- Se crea un registro de `Cita` por cada servicio seleccionado
- Todos los registros comparten:
  - Mismo `cliente_id`
  - Misma `fechaHora` y `fechaFin`
  - Mismo `negocio_id`
- Cada registro tiene su propio `servicio_id` y `precio`

### Cálculo de Duración y Precio
- **Duración total:** Suma de duraciones de todos los servicios
- **Precio total:** Suma de precios de todos los servicios
- **Fecha fin:** `fechaHora` + duración total

### Endpoint
```
POST /api/citas/multiple-servicios

Request:
{
  "clienteId": "uuid",
  "servicioIds": ["uuid1", "uuid2", "uuid3"],
  "fechaHora": "2024-01-15T10:00:00",
  "notas": "Opcional"
}

Response:
{
  "success": true,
  "data": {
    "citas": [ /* array de citas creadas */ ],
    "precioTotal": 150.00,
    "duracionTotal": 90
  }
}
```

## Configuración de Variables de Entorno

Todas las variables se configuran en `application.properties` del backend, consumiendo variables de entorno del sistema:

### Base de Datos (Google Cloud SQL)
```
DATABASE_URL=jdbc:postgresql://[IP]:5432/[DB]
DB_USER=[usuario]
DB_PASSWORD=[password]
```

### Seguridad
```
JWT_SECRET=[64+ caracteres]
```

### Integraciones
```
# Google OAuth
GOOGLE_CLIENT_ID=[client-id]

# Stripe
STRIPE_SECRET_KEY=[sk_...]
STRIPE_WEBHOOK_SECRET=[whsec_...]
STRIPE_PRICE_STARTER=[price_id]
STRIPE_PRICE_PROFESSIONAL=[price_id]
STRIPE_PRICE_ENTERPRISE=[price_id]

# Twilio (Professional y Enterprise)
TWILIO_ACCOUNT_SID=[AC...]
TWILIO_AUTH_TOKEN=[token]
TWILIO_PHONE_NUMBER=[+1...]
TWILIO_WHATSAPP_NUMBER=[whatsapp:+1...]

# SendGrid (Professional y Enterprise)
SENDGRID_API_KEY=[SG...]
SENDGRID_FROM_EMAIL=[email]
SENDGRID_FROM_NAME=[nombre]

# Google Cloud Storage
GCP_PROJECT_ID=[proyecto]
GCP_BUCKET_NAME=[bucket]
```

### Aplicación
```
FRONTEND_URL=https://tu-dominio.com
RATE_LIMIT_ENABLED=true
```

## Migraciones de Base de Datos

El proyecto usa **Flyway** para gestionar migraciones de base de datos de forma versionada.

### Ubicación
```
src/main/resources/db/migration/
```

### Convención de Nombres
```
V{version}__{descripcion}.sql

Ejemplos:
V1__initial_schema.sql
V2__add_user_roles.sql
V3__add_appointment_fields.sql
```

### Migraciones Actuales

1. **V1__initial_schema.sql** - Esquema inicial (negocios, usuarios, clientes, servicios, citas)
2. **V2__add_horarios_dias_libres.sql** - Horarios de trabajo y días libres
3. **V3__add_recurring_appointments.sql** - Soporte para citas recurrentes
4. **V4__add_white_label.sql** - Configuración de marca blanca
5. **V5__add_api_keys.sql** - Sistema de API keys
6. **V6__add_direccion_fields.sql** - Campos estructurados de dirección

## Logging

### Niveles Configurados
- **Root:** WARN
- **com.reservas:** INFO
- **Spring Web:** WARN
- **Spring Security:** WARN
- **Hibernate SQL:** WARN

### Qué se registra

**INFO:**
- Cambios de estado de citas (creación, confirmación, cancelación, completada)
- Creación/actualización de recursos (clientes, servicios, usuarios)
- Operaciones de pago y suscripciones
- Envío de recordatorios

**WARN:**
- Validaciones de límites de plan
- Intentos de acceso a funcionalidades no disponibles

**ERROR:**
- Errores en integraciones externas (Stripe, Twilio, SendGrid)
- Errores de autenticación/autorización
- Excepciones no controladas

### Ubicación de Logs
```
logs/cita-click.log
```

**Configuración:**
- Máximo 10MB por archivo
- Retención de 30 días
- Rotación automática

## Estructura del Proyecto

```
cita-click/
├── cita-click-backend/          # Backend Spring Boot
│   ├── src/main/java/com/reservas/
│   │   ├── controller/          # REST controllers
│   │   ├── service/             # Business logic
│   │   ├── repository/          # JPA repositories
│   │   ├── entity/              # JPA entities
│   │   ├── dto/                 # Data Transfer Objects
│   │   ├── security/            # JWT, filters, config
│   │   ├── exception/           # Custom exceptions
│   │   ├── scheduler/           # Scheduled tasks
│   │   └── util/                # Utilities
│   ├── src/main/resources/
│   │   ├── application.properties  # Configuración principal
│   │   └── db/migration/        # Flyway migrations
│   └── pom.xml                  # Maven dependencies
│
├── cita-click-frontend/         # Frontend Vue 3
│   ├── src/
│   │   ├── components/          # Componentes reutilizables
│   │   ├── views/               # Páginas/vistas
│   │   ├── stores/              # Pinia stores
│   │   ├── router/              # Vue Router config
│   │   ├── composables/         # Composition API composables
│   │   └── utils/               # Utilities (api, auth, etc)
│   ├── public/                  # Static assets
│   └── package.json             # npm dependencies
│
├── landing-page/                # Landing page (Vue/Nuxt)
│
└── README.md                    # Este archivo
```

## Desarrollo Local

### Pre-requisitos
- Java 21
- Node.js 18+
- PostgreSQL (o acceso a Cloud SQL)
- Maven 3.9+

### Backend

```bash
cd cita-click-backend

# Configurar variables de entorno (crear .env basado en .env.example)
cp .env.example .env

# Compilar y ejecutar
./mvnw spring-boot:run

# El backend estará en http://localhost:8080/api
```

### Frontend

```bash
cd cita-click-frontend

# Instalar dependencias
npm install

# Ejecutar en desarrollo
npm run dev

# El frontend estará en http://localhost:5173
```

## Despliegue en Producción

### Base de Datos
1. Crear instancia de Cloud SQL (PostgreSQL)
2. Configurar esquema inicial
3. Aplicar migraciones de Flyway
4. Configurar backups automáticos

### Backend (Cloud Run)
1. Configurar variables de entorno en Cloud Run
2. Configurar conexión a Cloud SQL
3. Deploy del contenedor
4. Configurar dominio personalizado

### Frontend
1. Build de producción: `npm run build`
2. Deploy en Cloud Storage + Cloud CDN o servicio de hosting
3. Configurar variables de entorno de producción

## Seguridad

### Autenticación
- JWT con tokens de acceso (7 días) y refresh (30 días)
- Google OAuth como alternativa
- Verificación de email obligatoria

### Autorización
- Basada en roles (ADMIN, USUARIO)
- Validación de pertenencia a negocio en cada request
- API Keys para acceso externo (solo Enterprise)

### Rate Limiting
- Límite de requests por IP
- Configuración en `application.properties`
- Protección contra abuso

### Datos Sensibles
- Contraseñas hasheadas con BCrypt
- JWT secrets de 64+ caracteres
- API keys de Stripe/Twilio/SendGrid en variables de entorno
- HTTPS obligatorio en producción

## Soporte y Mantenimiento

### Monitoreo
- Logs centralizados en Google Cloud Logging
- Métricas de uso en dashboard de admin
- Alertas de errores críticos

### Backups
- Base de datos: Backup automático diario en Cloud SQL
- Retención de 30 días
- Point-in-time recovery habilitado

## Roadmap

### Futuras Funcionalidades
- [ ] Integración con Google Calendar
- [ ] App móvil (iOS/Android)
- [ ] Sistema de reseñas y calificaciones
- [ ] Programa de fidelización de clientes
- [ ] Análisis predictivo de demanda
- [ ] Integración con redes sociales
- [ ] Multi-idioma

---

**Versión:** 1.0.0
**Última actualización:** Enero 2026
