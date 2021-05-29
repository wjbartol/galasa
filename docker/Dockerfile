ARG managersVersion
ARG dockerRepository
ARG branch
FROM ${dockerRepository}/galasadev/galasa-maven-managers:${managersVersion}-${branch}

ARG gitHash

RUN sed -i 's/\/managers"/\/obr"/' /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
RUN echo -n ${gitHash} > /usr/local/apache2/htdocs/obr.githash
