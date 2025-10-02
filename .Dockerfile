# Pobieramy oficjalny Tomcat 10 z JDK 21
FROM tomcat:10.1.9-jdk21

# Usuń domyślny ROOT
RUN rm -rf /usr/local/tomcat/webapps/ROOT

# Skopiuj swój plik WAR do Tomcata
COPY target/users_app.war /usr/local/tomcat/webapps/ROOT.war

# Ustawienie portu (Tomcat domyślnie 8080)
EXPOSE 8080

# Uruchom Tomcata w trybie foreground
CMD ["catalina.sh", "run"]