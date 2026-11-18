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
- El `oid` se lee del payload del JWT a mano (`extraerClaim`); la firma la valida el API Gateway.
- **Si Kafka falla**, se loguea y el reloj se devuelve igual. `KAFKA_MAX_BLOCK_MS` limita cuánto se espera.
- Productor: `acks=all`, idempotente, JSON sin headers de tipo. No crea tópicos.

## Variables de entorno
| Variable | Por defecto | Descripción |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka |
| `KAFKA_MAX_BLOCK_MS` | `3000` | Espera máxima al publicar |
| `SPRING_PROFILES_ACTIVE` | — | `prod` en EC2 (RabbitMQ en `rabbitmq1`) |

## Cómo probarlo
```bash
mvn clean package      # tests sin broker de Kafka (perfil test + KafkaTemplate mockeado)
docker build -t productos-service .
curl -H "Authorization: Bearer <jwt>" localhost:8081/productos/1
```
