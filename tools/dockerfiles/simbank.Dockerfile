# ==============================================================================
# 🏦 Galasa Ecosystem - SimBank Application Container Image (Pass 2 Layered Build)
# ==============================================================================
FROM galasadev/galasa-simplatform-amd64:0.18.0

LABEL name="simbank" \
      vendor="Galasa Ecosystem" \
      description="Galasa SimBank Mainframe Banking Simulator Container (Layered workspace build)"

WORKDIR /galasa

# Copy locally-built SimBank/SimPlatform JARs if present into /galasa/
COPY build/libs/ /galasa/
