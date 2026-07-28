$idea = 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.3'
$env:JAVA_HOME = "$idea\jbr"
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:mem:pedguide;DB_CLOSE_DELAY=-1'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_JPA_DATABASE_PLATFORM = 'org.hibernate.dialect.H2Dialect'
$env:SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT = 'org.hibernate.dialect.H2Dialect'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'create-drop'
$env:SERVER_PORT = '8090'
$env:SCHOOL_CODE = '7'
$env:SERVER_SERVLET_SESSION_COOKIE_SECURE = 'false'
$env:APP_ADMIN_USERNAME = 'admin'
$env:APP_ADMIN_PASSWORD = 'admin'
$env:APP_ADMIN_FULL_NAME = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String('0JzQtdGC0L7QtNC40YHRgiDQn9C10YLRgNC+0LLQsCDQkNC90L3QsCDQodC10YDQs9C10LXQstC90LA=')
)

$maven = "$idea\plugins\maven\lib\maven3\bin\mvn.cmd"
& $maven -o -q '-Dmaven.repo.local=C:\Users\dimah\.m2\repository' `
    '-DskipTests' '-Dspring-boot.run.useTestClasspath=true' 'spring-boot:run' `
    1> 'C:\Users\dimah\IdeaProjects\Tarification\tmp\ped-guide-app.out.log' `
    2> 'C:\Users\dimah\IdeaProjects\Tarification\tmp\ped-guide-app.err.log'
