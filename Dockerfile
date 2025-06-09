FROM amazoncorretto:21 
EXPOSE 8890
RUN mkdir /opt/app
COPY target/uberjar/centripetal-indicators-service-0.1.0-SNAPSHOT-standalone.jar /opt/app/app.jar
ENV PORT=8890
ENV HOST=0.0.0.0
CMD ["java", "-jar", "/opt/app/app.jar"]

