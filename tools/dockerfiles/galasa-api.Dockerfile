# ==============================================================================
# 🚀 Galasa Ecosystem - API Server Container Image (Pass 2 Layered Build)
# ==============================================================================
FROM galasadev/galasa-boot-embedded-amd64:0.19.1

LABEL name="galasa-api" \
      vendor="Galasa Ecosystem" \
      description="Galasa API Server microservice container (Layered workspace build)"

WORKDIR /galasa

# Copy locally-built OSGi JARs on top of base image
COPY build/libs/ /galasa/
