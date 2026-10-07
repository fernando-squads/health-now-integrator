# Health Now Integrator

Aplicação Java responsável pela integração de serviços do Health Now.

## Requisitos

- Java 8
- Maven 3.6 ou superior
- PostgreSQL (quando a integração com banco de dados for configurada)

## Compilar

```bash
mvn clean compile
```

## Executar

```bash
java -cp target/classes br.com.bancadoingresso.integrator.Application
```

## Dependências

O projeto utiliza o driver JDBC do PostgreSQL (`org.postgresql:postgresql`).
