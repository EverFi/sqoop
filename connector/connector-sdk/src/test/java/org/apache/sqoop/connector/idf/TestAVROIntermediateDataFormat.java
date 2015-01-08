/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sqoop.connector.idf;

import static org.apache.sqoop.connector.common.SqoopAvroUtils.createEnumSchema;
import static org.apache.sqoop.connector.common.TestSqoopIDFUtils.getByteFieldString;
import static org.junit.Assert.assertEquals;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.apache.sqoop.connector.common.SqoopAvroUtils;
import org.apache.sqoop.schema.Schema;
import org.apache.sqoop.schema.type.Array;
import org.apache.sqoop.schema.type.Binary;
import org.apache.sqoop.schema.type.Bit;
import org.apache.sqoop.schema.type.Column;
import org.apache.sqoop.schema.type.FixedPoint;
import org.apache.sqoop.schema.type.Text;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestAVROIntermediateDataFormat {

  private AVROIntermediateDataFormat dataFormat;
  private org.apache.avro.Schema avroSchema;
  private final static String csvArray = "'[[11,11],[14,15]]'";
  private final static String map = "'{\"testKey\":\"testValue\"}'";
  private final static String csvSet = "'[[11,12],[14,15]]'";
  private final static String csvDate = "'2014-10-01'";
  private final static String csvDateTime = "'2014-10-01 12:00:00.000'";
  private final static String csvTime = "'12:59:59'";
  private Column enumCol;
  // no time zone
  private final static LocalDateTime dateTime = new org.joda.time.LocalDateTime(2014, 10, 01, 12, 0, 0);
  private final static org.joda.time.LocalTime time = new org.joda.time.LocalTime(12, 59, 59);
  private final static org.joda.time.LocalDate date = new org.joda.time.LocalDate(2014, 10, 01);

  @Before
  public void setUp() {
    createAvroIDF();
  }

  private void createAvroIDF() {
    Schema sqoopSchema = new Schema("test");
    Set<String> options = new HashSet<String>();
    options.add("ENUM");
    options.add("NUME");
    enumCol = new org.apache.sqoop.schema.type.Enum("seven").setOptions(options);
    sqoopSchema.addColumn(new FixedPoint("one")).addColumn(new FixedPoint("two", 2L, false)).addColumn(new Text("three"))
        .addColumn(new Text("four")).addColumn(new Binary("five")).addColumn(new Text("six")).addColumn(enumCol)
        .addColumn(new Array("eight", new Array("array", new FixedPoint("ft"))))
        .addColumn(new org.apache.sqoop.schema.type.Map("nine", new Text("t1"), new Text("t2"))).addColumn(new Bit("ten"))
        .addColumn(new org.apache.sqoop.schema.type.DateTime("eleven", true, false))
        .addColumn(new org.apache.sqoop.schema.type.Time("twelve", false))
        .addColumn(new org.apache.sqoop.schema.type.Date("thirteen"))
        .addColumn(new org.apache.sqoop.schema.type.FloatingPoint("fourteen"))
        .addColumn(new org.apache.sqoop.schema.type.Set("fifteen", new Array("set", new FixedPoint("ftw"))));
    dataFormat = new AVROIntermediateDataFormat(sqoopSchema);
    avroSchema = SqoopAvroUtils.createAvroSchema(sqoopSchema);
  }

  /**
   * setCSVGetData setCSVGetObjectArray setCSVGetCSV
   */
  @Test
  public void testInputAsCSVTextInAndDataOut() {

    String csvText = "10,34,'54','random data'," + getByteFieldString(new byte[] { (byte) -112, (byte) 54 }) + ",'"
        + String.valueOf(0x0A) + "','ENUM'," + csvArray + "," + map + ",true," + csvDateTime + "," + csvTime + "," + csvDate
        + ",13.44," + csvSet;
    dataFormat.setCSVTextData(csvText);
    GenericRecord avroObject = createAvroGenericRecord();
    assertEquals(avroObject.toString(), dataFormat.getData().toString());
  }

  @Test
  public void testInputAsCSVTextInAndObjectArrayOut() {
    String csvText = "10,34,'54','random data'," + getByteFieldString(new byte[] { (byte) -112, (byte) 54 }) + ",'"
        + String.valueOf(0x0A) + "','ENUM'," + csvArray + "," + map + ",true," + csvDateTime + "," + csvTime + "," + csvDate
        + ",13.44," + csvSet;
    dataFormat.setCSVTextData(csvText);
    assertEquals(dataFormat.getObjectData().length, 15);
    assertObjectArray();

  }

  private void assertObjectArray() {
    Object[] out = dataFormat.getObjectData();
    assertEquals(10L, out[0]);
    assertEquals(34, out[1]);
    assertEquals("54", out[2]);
    assertEquals("random data", out[3]);
    assertEquals(-112, ((byte[]) out[4])[0]);
    assertEquals(54, ((byte[]) out[4])[1]);
    assertEquals("10", out[5]);
    assertEquals("ENUM", out[6]);

    Object[] givenArrayOne = new Object[2];
    givenArrayOne[0] = 11;
    givenArrayOne[1] = 11;
    Object[] givenArrayTwo = new Object[2];
    givenArrayTwo[0] = 14;
    givenArrayTwo[1] = 15;
    Object[] arrayOfArrays = new Object[2];
    arrayOfArrays[0] = givenArrayOne;
    arrayOfArrays[1] = givenArrayTwo;
    Map<Object, Object> map = new HashMap<Object, Object>();
    map.put("testKey", "testValue");
    Object[] set0 = new Object[2];
    set0[0] = 11;
    set0[1] = 12;
    Object[] set1 = new Object[2];
    set1[0] = 14;
    set1[1] = 15;
    Object[] set = new Object[2];
    set[0] = set0;
    set[1] = set1;
    out[14] = set;
    assertEquals(arrayOfArrays.length, 2);
    assertEquals(Arrays.deepToString(arrayOfArrays), Arrays.deepToString((Object[]) out[7]));
    assertEquals(map, out[8]);
    assertEquals(true, out[9]);
    assertEquals(dateTime, out[10]);
    assertEquals(time, out[11]);
    assertEquals(date, out[12]);
    assertEquals(13.44, out[13]);
    assertEquals(set.length, 2);
    assertEquals(Arrays.deepToString(set), Arrays.deepToString((Object[]) out[14]));

  }

  @Test
  public void testInputAsCSVTextInCSVTextOut() {
    String csvText = "10,34,'54','random data'," + getByteFieldString(new byte[] { (byte) -112, (byte) 54 }) + ",'"
        + String.valueOf(0x0A) + "','ENUM'," + csvArray + "," + map + ",true," + csvDateTime + "," + csvTime + "," + csvDate
        + ",13.44," + csvSet;
    dataFormat.setCSVTextData(csvText);
    assertEquals(csvText, dataFormat.getCSVTextData());
  }

  private GenericRecord createAvroGenericRecord() {
    GenericRecord avroObject = new GenericData.Record(avroSchema);
    avroObject.put("one", 10L);
    avroObject.put("two", 34);
    avroObject.put("three", new Utf8("54"));
    avroObject.put("four", new Utf8("random data"));
    // store byte array in byte buffer
    byte[] b = new byte[] { (byte) -112, (byte) 54 };
    avroObject.put("five", ByteBuffer.wrap(b));
    avroObject.put("six", new Utf8(String.valueOf(0x0A)));
    avroObject.put("seven", new GenericData.EnumSymbol(createEnumSchema(enumCol), "ENUM"));

    List<Object> givenArrayOne = new ArrayList<Object>();
    givenArrayOne.add(11);
    givenArrayOne.add(11);
    List<Object> givenArrayTwo = new ArrayList<Object>();
    givenArrayTwo.add(14);
    givenArrayTwo.add(15);
    List<Object> arrayOfArrays = new ArrayList<Object>();

    arrayOfArrays.add(givenArrayOne);
    arrayOfArrays.add(givenArrayTwo);

    Map<Object, Object> map = new HashMap<Object, Object>();
    map.put("testKey", "testValue");

    avroObject.put("eight", arrayOfArrays);
    avroObject.put("nine", map);
    avroObject.put("ten", true);

    // expect dates as strings
    avroObject.put("eleven", dateTime.toDate().getTime());
    avroObject.put("twelve", time.toDateTimeToday().getMillis());
    avroObject.put("thirteen", date.toDate().getTime());
    avroObject.put("fourteen", 13.44);
    List<Object> givenSetOne = new ArrayList<Object>();
    givenSetOne.add(11);
    givenSetOne.add(12);
    List<Object> givenSetTwo = new ArrayList<Object>();
    givenSetTwo.add(14);
    givenSetTwo.add(15);
    List<Object> set = new ArrayList<Object>();
    set.add(givenSetOne);
    set.add(givenSetTwo);
    avroObject.put("fifteen", set);
    return avroObject;
  }

  /**
   * setDataGetCSV setDataGetObjectArray setDataGetData
   */
  @Test
  public void testInputAsDataInAndCSVOut() {

    String csvExpected = "10,34,'54','random data'," + getByteFieldString(new byte[] { (byte) -112, (byte) 54 }) + ",'"
        + String.valueOf(0x0A) + "','ENUM'," + csvArray + "," + map + ",true," + csvDateTime + "," + csvTime + "," + csvDate
        + ",13.44," + csvSet;
    dataFormat.setData(createAvroGenericRecord());
    assertEquals(csvExpected, dataFormat.getCSVTextData());
  }

  @Test
  public void testInputAsDataInAndObjectArrayOut() {
    GenericRecord avroObject = createAvroGenericRecord();
    dataFormat.setData(avroObject);
    assertObjectArray();
  }

  @Test
  public void testInputAsDataInAndDataOut() {
    GenericRecord avroObject = createAvroGenericRecord();
    dataFormat.setData(avroObject);
    assertEquals(avroObject, dataFormat.getData());
  }

  private Object[] createObjectArray() {
    Object[] out = new Object[15];
    out[0] = 10L;
    out[1] = 34;
    out[2] = "54";
    out[3] = "random data";
    out[4] = new byte[] { (byte) -112, (byte) 54 };
    out[5] = String.valueOf(0x0A);
    out[6] = "ENUM";

    Object[] givenArrayOne = new Object[2];
    givenArrayOne[0] = 11;
    givenArrayOne[1] = 11;
    Object[] givenArrayTwo = new Object[2];
    givenArrayTwo[0] = 14;
    givenArrayTwo[1] = 15;

    Object[] arrayOfArrays = new Object[2];
    arrayOfArrays[0] = givenArrayOne;
    arrayOfArrays[1] = givenArrayTwo;

    Map<Object, Object> map = new HashMap<Object, Object>();
    map.put("testKey", "testValue");

    out[7] = arrayOfArrays;
    out[8] = map;
    out[9] = true;
    out[10] = dateTime;
    out[11] = time;
    out[12] = date;

    out[13] = 13.44;
    Object[] set0 = new Object[2];
    set0[0] = 11;
    set0[1] = 12;
    Object[] set1 = new Object[2];
    set1[0] = 14;
    set1[1] = 15;

    Object[] set = new Object[2];
    set[0] = set0;
    set[1] = set1;
    out[14] = set;
    return out;
  }

  /**
   * setObjectArrayGetData setObjectArrayGetCSV setObjectArrayGetObjectArray
   */
  @Test
  public void testInputAsObjectArrayInAndDataOut() {

    Object[] out = createObjectArray();
    dataFormat.setObjectData(out);
    GenericRecord avroObject = createAvroGenericRecord();
    // SQOOP-SQOOP-1975: direct object compare will fail unless we use the Avro complex types
    assertEquals(avroObject.toString(), dataFormat.getData().toString());

  }

  @Test
  public void testInputAsObjectArrayInAndCSVOut() {
    Object[] out = createObjectArray();
    dataFormat.setObjectData(out);
    String csvText = "10,34,'54','random data'," + getByteFieldString(new byte[] { (byte) -112, (byte) 54 }) + ",'"
        + String.valueOf(0x0A) + "','ENUM'," + csvArray + "," + map + ",true," + csvDateTime + "," + csvTime + "," + csvDate
        + ",13.44," + csvSet;
    assertEquals(csvText, dataFormat.getCSVTextData());
  }

  @Test
  public void testInputAsObjectArrayInAndObjectArrayOut() {
    Object[] out = createObjectArray();
    dataFormat.setObjectData(out);
    assertObjectArray();
  }
}
