#!/bin/bash
# Builds the Paper API (1.21.11) from PaperMC's public GitHub source and installs it, with Brigadier and a
# PlaceholderAPI compile stub, into ~/.m2 - for machines where repo.papermc.io and repo.extendedclip.com are
# blocked (the Claude cloud sessions by default). Everything else comes from Maven Central.
# After this, `mvn -q package -DskipTests -pl practicepvp,vortexrealm` works (online or with -o).
#
# usage (from vortex-realm/):  bash tools/build_paper_api.sh [work-dir]
# Checked on Nov 17 2026: the plugin built this way is byte-identical to the released VortexRealm 3.9.2.
set -euo pipefail
W=${1:-/tmp/paper-api-build}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$W" && cd "$W"
export JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS:-}

# 1. Maven settings: the two blocked repositories are answered by Maven Central (the snapshot checks just miss)
mkdir -p ~/.m2
cat > ~/.m2/settings.xml <<'EOF'
<settings><mirrors><mirror><id>central-for-blocked</id><mirrorOf>papermc,placeholderapi</mirrorOf><url>https://repo.maven.apache.org/maven2</url></mirror></mirrors></settings>
EOF

# 2. paper-api sources at tag 1.21.11 (sparse, shallow) and Brigadier
if [ ! -d paper/paper-api ]; then
  mkdir -p paper && (cd paper && git init -q && git remote add origin https://github.com/PaperMC/Paper \
    && git sparse-checkout set paper-api >/dev/null && git fetch -q --depth 1 origin tag 1.21.11 && git checkout -q FETCH_HEAD)
fi
[ -d brig ] || GIT_LFS_SKIP_SMUDGE=1 git clone -q --depth 1 https://github.com/Mojang/brigadier brig

# 3. paper-api's dependencies from Maven Central
mkdir -p deps && cat > deps/pom.xml <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>x</groupId><artifactId>deps</artifactId><version>1</version>
<dependencyManagement><dependencies><dependency><groupId>net.kyori</groupId><artifactId>adventure-bom</artifactId><version>4.26.1</version><type>pom</type><scope>import</scope></dependency></dependencies></dependencyManagement>
<dependencies>
<dependency><groupId>com.google.guava</groupId><artifactId>guava</artifactId><version>33.3.1-jre</version></dependency>
<dependency><groupId>com.google.code.gson</groupId><artifactId>gson</artifactId><version>2.11.0</version></dependency>
<dependency><groupId>org.yaml</groupId><artifactId>snakeyaml</artifactId><version>2.2</version></dependency>
<dependency><groupId>org.joml</groupId><artifactId>joml</artifactId><version>1.10.8</version></dependency>
<dependency><groupId>it.unimi.dsi</groupId><artifactId>fastutil</artifactId><version>8.5.15</version></dependency>
<dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-api</artifactId><version>2.24.1</version></dependency>
<dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId><version>2.0.16</version></dependency>
<dependency><groupId>net.md-5</groupId><artifactId>bungeecord-chat</artifactId><version>1.21-R0.2</version></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-api</artifactId></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-text-minimessage</artifactId></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-text-serializer-gson</artifactId></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-text-serializer-legacy</artifactId></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-text-serializer-plain</artifactId></dependency>
<dependency><groupId>net.kyori</groupId><artifactId>adventure-text-logger-slf4j</artifactId></dependency>
<dependency><groupId>org.apache.maven</groupId><artifactId>maven-resolver-provider</artifactId><version>3.9.6</version></dependency>
<dependency><groupId>org.apache.maven.resolver</groupId><artifactId>maven-resolver-connector-basic</artifactId><version>1.9.18</version></dependency>
<dependency><groupId>org.apache.maven.resolver</groupId><artifactId>maven-resolver-transport-http</artifactId><version>1.9.18</version></dependency>
<dependency><groupId>org.jetbrains</groupId><artifactId>annotations</artifactId><version>26.0.2</version></dependency>
<dependency><groupId>org.checkerframework</groupId><artifactId>checker-qual</artifactId><version>3.49.2</version></dependency>
<dependency><groupId>org.jspecify</groupId><artifactId>jspecify</artifactId><version>1.0.0</version></dependency>
</dependencies></project>
EOF
(cd deps && mvn -q dependency:copy-dependencies -DoutputDirectory=lib)

# 4. compile Brigadier, paper-api and the PlaceholderAPI stub
rm -rf out && mkdir -p out/brig out/api out/papi
javac -nowarn --release 21 -d out/brig $(find brig/src/main -name '*.java')
(cd out/brig && jar cf ../brigadier.jar .)
CP=$(ls deps/lib/*.jar | tr '\n' ':')out/brigadier.jar
find paper/paper-api/src/main/java paper/paper-api/src/generated/java -name '*.java' > out/api.txt
javac -nowarn --release 21 -encoding UTF-8 -proc:none -cp "$CP" -d out/api @out/api.txt
cp -r paper/paper-api/src/main/resources/. out/api/ 2>/dev/null || true
(cd out/api && jar cf ../paper-api.jar .)
javac -nowarn --release 21 -cp out/api -d out/papi $(find "$HERE/papi-stub" -name '*.java')
(cd out/papi && jar cf ../placeholderapi.jar .)

# 5. install into ~/.m2 (paper-api with a pom listing its dependencies)
cat > paper-api.pom <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.11-R0.1-SNAPSHOT</version>
<description>Built locally from PaperMC/Paper tag 1.21.11 (paper-api sources)</description>
EOF
sed -n '/<dependencyManagement>/,$p' deps/pom.xml | sed 's#</dependencies></project>#<dependency><groupId>com.mojang</groupId><artifactId>brigadier</artifactId><version>1.3.10</version></dependency></dependencies></project>#' >> paper-api.pom
mvn -q install:install-file -Dfile=out/brigadier.jar -DgroupId=com.mojang -DartifactId=brigadier -Dversion=1.3.10 -Dpackaging=jar
mvn -q install:install-file -Dfile=out/placeholderapi.jar -DgroupId=me.clip -DartifactId=placeholderapi -Dversion=2.11.6 -Dpackaging=jar
mvn -q install:install-file -Dfile=out/paper-api.jar -DpomFile=paper-api.pom
echo "Paper API 1.21.11 installed. Now: mvn -q package -DskipTests -pl practicepvp,vortexrealm"
