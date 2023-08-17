---
sidebar_position: 2
title: Como usarlo
description: Como utilizar Ezlib
---

:::info

Ezlib usa el mismo formato que gradle para agregar dependencias

:::

## Basic

Ezlib ofrece métodos fáciles de entender para cargar todas las dependencias que necesites en tiempo de ejecución dentro de un class loader.

Aquí se muestra un ejemplo para cargar dependencias en un child class loader.

```java
// Crear una instancia de Ezlib que por defecto guarda la librerías en el path "folder"
Ezlib ezlib = new Ezlib();
// También puedes crear la instancia especificando el path
Ezlib ezlib = new Ezlib(new File("folder/path"));

// Cargar ezlib
ezlib.init();

// Cargar una dependencia en un class loader menor
ezlib.dependency("commons-io:commons-io:2.11.0").load();

// Cargar una dependencia desde un repositorio en específico
ezlib.dependency("com.saicone.rtag:rtag:1.3.0").repository("https://jitpack.io/").load();

// Tambien puedes cambiar el repositoria por defecto
ezlib.setDefaultRepository("URL de repositorio");
```

## ClassLoader principal

Ezlib permite cargar la dependencias en el class loader actual o principal y especificar el repositorio antes del metodo de cargar.

```java
Ezlib ezlib = new Ezlib();
// Cargar ezlib
ezlib.init();

// Cargar una dependencia en el class loader actual o principal
ezlib.dependency("commons-io:commons-io:2.11.0").parent(true).load();

// Cargar desde un repositorio en específico
ezlib.dependency("com.saicone.rtag:rtag:1.1.0")
        .repository("https://jitpack.io/")
        .parent(false)
        .load();
```

## Recolocación

Ezlib usa la librería [jar-relocator](https://github.com/lucko/jar-relocator), así que puedes recolocar los imports de las dependencias que quieres cargar.

Aquí se verá un ejemplo para cargar la librerías de Redis con todas sus dependencias en el parent class loader.

```java
Map<String, String> relocations = new HashMap();
relocations.put("com.google.gson", "myproject.path.libs.gson");
relocations.put("org.apache.commons.pool2", "myproject.path.libs.pool2");
relocations.put("org.json", "myproject.path.libs.json");
relocations.put("org.slf4j", "myproject.path.libs.slf4j");
relocations.put("redis.clients.jedis", "myproject.path.libs.jedis");

Ezlib ezlib = new Ezlib();
ezlib.init();

// Cargar primero todas las librerías necesarias
ezlib.dependency("com.google.gson:gson:2.8.9").relocations(map).parent(true).load();
ezlib.dependency("org.apache.commons:commons-pool2:2.11.1").relocations(map).parent(true).load();
ezlib.dependency("org.json:json:20211205").relocations(map).parent(true).load();
ezlib.dependency("org.slf4j:slf4j-api:1.7.32").relocations(map).parent(true).load();

// Luego cargar la librería de Redis
ezlib.dependency("redis.clients:jedis:4.2.2").relocations(map).parent(true).load();
```

:::warning

Recuerda recolocar los imports de tu proyecto cuando lo vayas a compilar ya sea usando Gradle ShadowJar o Maven Shade, para que la recolocación de Ezlib tenga sentido con tu proyecto actual, además excluye el class que estas utilizando para cargar las dependencias al momento de recolocar los imports de tu proyecto ya que los Strings de ese class también son cambiados.

:::