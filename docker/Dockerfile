ARG dockerRepository
ARG branch
FROM ${dockerRepository}/galasadev/galasa-bld-binary:main AS buildutils

FROM ${dockerRepository}/galasadev/galasa-maven-managers:${branch}

RUN sed -i 's/\/managers"/\/obr"/' /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
COPY obr.githash /usr/local/apache2/htdocs/obr.githash

COPY --from=buildutils /usr/local/apache2/htdocs/galasabld-linux-amd64 /usr/local/apache2/htdocs/galasabld
RUN chmod +x /usr/local/apache2/htdocs/galasabld

