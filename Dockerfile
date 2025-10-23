# Pobieramy oficjalny Tomcat 10 z JDK 21
FROM tomcat:10.1.24-jdk21-temurin

# Usuń domyślny ROOT
RUN rm -rf /usr/local/tomcat/webapps/ROOT

# Skopiuj swój plik WAR do Tomcata
COPY target/users_app.jar /usr/local/tomcat/webapps/ROOT.jar

# Ustawienie portu (Tomcat domyślnie 8080)
EXPOSE 8080

# Uruchom Tomcata w trybie foreground
CMD ["catalina.sh", "run"]