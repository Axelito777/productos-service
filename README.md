# productos-service (puerto 8081)

Catálogo y stock de relojes de Pedidos360.
- **RabbitMQ:** consume `stock-queue` (exchange `orders.direct`, routing key `order.created`) para descontar
  stock cuando se crea un pedido, con DLQ `stock-queue.dlq`.
- **Kafka (nuevo):** publica `PRODUCT_VIEWED` en el tópico `user-events` para ms-analytics-recommendations.

## Endpoints
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/productos?categoria=Deportivo` | Lista (filtro opcional por categoría) |
| GET | `/productos/{id}` | Detalle; con token publica `PRODUCT_VIEWED` |
| POST | `/productos` | Crea un reloj |
| PUT | `/productos/{id}` | Actualiza |
| DELETE | `/productos/{id}` | Elimina |

## Seguridad (JWT de Azure Entra ID)
El servicio valida el token por su cuenta, además del API Gateway (`SecurityConfig`):
- **Firma** con las llaves públicas de Azure (JWKS, se descargan con el primer token, no al arrancar).
- **Emisor** `https://login.microsoftonline.com/<tenant>/v2.0`, **audiencia** `api://<client-id>` o `<client-id>`,
  y **expiración** (60 s de tolerancia).
- `scp` → `SCOPE_xxx`, `roles` → `ROLE_xxx`. GET requiere `productos.read`; POST/PUT/DELETE `productos.write`.
  Cualquier otra ruta (incluida `/h2-console`) está denegada.
- **401**: sin token o token inválido (firma, emisor, audiencia, expirado). **403**: token válido sin el scope.

Body de POST/PUT:
```json
{ "marca": "Casio", "modelo": "G-Shock GA-2100", "descripcion": "Resistente", "precio": 99990,
  "stock": 10, "categoria": "Deportivo", "imagenUrl": "https://..." }
```

## Evento PRODUCT_VIEWED
Al llamar `GET /productos/{id}` **con** `Authorization: Bearer <jwt>`, se publica (key = `oid`):
```json
{ "eventId": "uuid", "userId": "oid-del-token", "eventType": "PRODUCT_VIEWED", "productId": 1,
  "productCategory": "Deportivo", "brand": "Casio", "quantity": null, "timestamp": "2026-11-16T15:00:00Z" }
```
- Sin token no se publica (usuario anónimo). Un id inexistente responde 404 y no publica.
- El `oid` se lee del payload del JWT a mano (`extraerClaim`); el token ya fue validado por `SecurityConfig`.
- **Si Kafka falla**, se loguea y el reloj se devuelve igual. `KAFKA_MAX_BLOCK_MS` limita cuánto se espera.
- Productor: `acks=all`, idempotente, JSON sin headers de tipo. No crea tópicos.

## Variables de entorno
| Variable | Por defecto | Descripción |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka |
| `KAFKA_MAX_BLOCK_MS` | `3000` | Espera máxima al publicar |
| `AZURE_TENANT_ID` | tenant del proyecto | Tenant de Entra ID (emisor y JWKS) |
| `AZURE_CLIENT_ID` | client ID de la API | Audiencia válida del token |
| `SPRING_PROFILES_ACTIVE` | — | `prod` en EC2 (RabbitMQ en `rabbitmq1`) |

## Cómo probarlo
```bash
mvn clean package      # tests sin Kafka ni internet (tokens firmados con llave RSA de prueba)
docker build -t productos-service .
curl -H "Authorization: Bearer <jwt>" localhost:8081/productos/1
```
