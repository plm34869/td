FROM alpine:3.14

RUN apk update && \
    apk upgrade && \
    apk add --update alpine-sdk linux-headers git zlib-dev openssl-dev gperf php cmake openjdk11

COPY . /.td_app

WORKDIR /.td_app/
RUN rm -rf build && mkdir build
WORKDIR /.td_app/build

RUN cmake -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk/ -DCMAKE_INSTALL_PREFIX:PATH=../example/java/td -DTD_ENABLE_JNI=ON .. && \
    cmake --build . --target install

WORKDIR /.td_app/example/java
RUN rm -rf build && mkdir build

WORKDIR /.td_app/example/java/build
RUN cmake -DCMAKE_BUILD_TYPE=Release -DJAVA_HOME=/usr/lib/jvm/java-11-openjdk/ -DCMAKE_INSTALL_PREFIX:PATH=../../../tdlib -DTd_DIR:PATH=$(readlink -f ../td/lib/cmake/Td) .. && \
    cmake --build . --target install

WORKDIR /.td_app/tdlib/bin
CMD /usr/lib/jvm/java-11-openjdk/bin/java '-Djava.library.path=.' org/drinkless/tdlib/example/Example