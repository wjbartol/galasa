ARG gradleVersion
ARG dockerRepository
ARG branch
FROM ${dockerRepository}/galasadev/galasa-maven-gradle:${gradleVersion}-${branch}

ARG gitHash

RUN sed -i 's/\/gradle"/\/maven"/' /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
RUN echo -n ${gitHash} > /usr/local/apache2/htdocs/maven.githash
