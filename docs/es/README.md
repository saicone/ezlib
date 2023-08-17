---
sidebar_position: 1
title: Ezlib
description: Cargador de librerías/dependencias en un solo class de Java
---

Bienvenid@ a la wiki de Ezlib, en esta encontrarás información de como utilizar esta compacta librería.

## Introducción

Ezib es una librería creada con el fin de ofrecer una forma fácil para cargar dependencias de proyectos programados en Java mientras están siendo ejecutados, incluyendo compatibilidad con la recolocación de packages.

## Requisitos

*  Mínimo **Java 8**

## Dependencia

![version](https://img.shields.io/github/v/tag/saicone/ezlib?label=versión%20actual&style=for-the-badge)

Ezlib es completamente compatible como una implementación dentro de tu proyecto.

Puedes copiar y pegar el [class principal de Ezlib](https://github.com/saicone/ezlib/blob/master/src/main/java/com/saicone/ezlib/Ezlib.java) para usarlo, o bien puedes agregarlo como una implementación en tu proyecto.

```mdx-code-block
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

<Tabs>
<TabItem value="groovy" label="build.gradle" default>

```groovy
plugins {
    id 'com.github.johnrengelman.shadow' version '8.1.1'
}

repositories {
    maven { url 'https://jitpack.io' }
}

// Usar solo ezlib
dependencies {
    implementation 'com.saicone.ezlib:ezlib:VERSION'
}

// Usar ezlib loader
dependencies {
    implementation 'com.saicone.ezlib:loader:VERSION'
    // Usar los annotations
    compileOnly 'com.saicone.ezlib:annotations:VERSION'
    annotationProcessor 'com.saicone.ezlib:annotations:VERSION'
}

jar.dependsOn (shadowJar)

shadowJar {
    relocate 'com.saicone.ezlib', project.group + '.ezlib'
}
```

</TabItem>
<TabItem value="kotlin" label="build.gradle.kts">

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    maven("https://jitpack.io")
}

// Usar solo ezlib
dependencies {
    implementation("com.saicone.ezlib:ezlib:VERSION")
}

// Usar ezlib loader
dependencies {
    implementation("com.saicone.ezlib:loader:VERSION")
    // Usar los annotations
    compileOnly("com.saicone.ezlib:annotations:VERSION")
    annotationProcessor("com.saicone.ezlib:annotations:VERSION")
}

tasks {
    jar {
        dependsOn(tasks.shadowJar)
    }

    shadowJar {
        relocate("com.saicone.ezlib", "${project.group}.ezlib")
    }
}
```

</TabItem>
<TabItem value="maven" label="pom.xml">

```xml
<repositories>
    <repository>
        <id>Jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</ repositories>

<dependencies>
    <!-- Usar ezlib -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>ezlib</artifactId>
        <version>VERSION</version>
        <scope>compile</scope>
    </dependency>
    <!-- Usar ezlib loader -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>loader</artifactId>
        <version>VERSION</version>
        <scope>compile</scope>
    </dependency>
    <!-- Usar los annotations -->
    <dependency>
        <groupId>com.saicone.ezlib</groupId>
        <artifactId>annotations</artifactId>
        <version>VERSION</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.3.0</version>
        <configuration>
            <artifactSet>
                <includes>
                    <include>com.saicone.ezlib:ezlib</include>
                    <include>com.saicone.ezlib:loader</include>
                </includes>
            </artifactSet>
            <relocations>
                <relocation>
                    <pattern>com.saicone.ezlib</pattern>
                    <shadedPattern>${project.groupId}.ezlib</shadedPattern>
                </relocation>
            </relocations>
        </configuration>
        <executions>
            <execution>
                <phase>package</phase>
                <goals>
                    <goal>shade</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</build>
```

</TabItem>
</Tabs>
