ARG branch
ARG dockerRepository
FROM ${dockerRepository}/galasadev/galasa-maven-wrapping:${branch}

COPY repo/ /usr/local/apache2/htdocs/
COPY gradle.githash /usr/local/apache2/htdocs/gradle.githash
