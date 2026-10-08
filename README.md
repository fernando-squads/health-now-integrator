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
mvn clean package
java -jar target/integrator-1.0.0-jar-with-dependencies.jar
```

## Dependências

O projeto utiliza o driver JDBC do PostgreSQL (`org.postgresql:postgresql`).

## Extração local

Após informar a conexão e clicar em Próximo, o integrador consulta o banco em modo
somente leitura e gera arquivos JSONL, manifesto, relatório de reconciliação e um ZIP
em `exports/<run_id>/`. Informe também a API HTTPS, instalação e execução autorizada.
No primeiro uso, ative a identidade com o código emitido pelo administrador e uma
senha local forte. O andamento aparece na tela.

Configuração, consultas, relacionamentos e testes: [Database extraction](docs/DATABASE_EXTRACTION.md).

O `IntegratorService` solicita uma autorização temporária à API, envia o ZIP ao S3
e confirma sua disponibilidade. O JAR não utiliza `.env`, credenciais AWS ou login
ADM. A chave individual é gerada e protegida localmente. Use **Retomar ZIP** para
recuperar uma entrega sem gerar outro arquivo. Registro não significa carga
processada: o processamento pertence ao Scheduler.
Veja [Load delivery](docs/LOAD_DELIVERY.md) e [Architecture](docs/ARCHITECTURE.md).
