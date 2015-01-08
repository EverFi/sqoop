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

import static org.apache.sqoop.connector.common.SqoopIDFUtils.*;
import static org.apache.sqoop.connector.common.SqoopAvroUtils.*;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.util.Utf8;
import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.schema.type.Column;
import org.apache.sqoop.utils.ClassUtils;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IDF representing the intermediate format in Avro object
 */
public class AVROIntermediateDataFormat extends IntermediateDataFormat<GenericRecord> {

  private Schema avroSchema;

  // need this default constructor for reflection magic used in execution engine
  public AVROIntermediateDataFormat() {
  }

  // We need schema at all times
  public AVROIntermediateDataFormat(org.apache.sqoop.schema.Schema schema) {
    setSchema(schema);
    avroSchema = createAvroSchema(schema);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setCSVTextData(String text) {
    // convert the CSV text to avro
    this.data = toAVRO(text);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getCSVTextData() {
    // convert avro to sqoop CSV
    return toCSV(data);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setObjectData(Object[] data) {
    // convert the object array to avro
    this.data = toAVRO(data);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Object[] getObjectData() {
    // convert avro to object array
    return toObject(data);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void write(DataOutput out) throws IOException {
    // do we need to write the schema?
    DatumWriter<GenericRecord> writer = new GenericDatumWriter<GenericRecord>(avroSchema);
    BinaryEncoder encoder = EncoderFactory.get().directBinaryEncoder((DataOutputStream) out, null);
    writer.write(data, encoder);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void read(DataInput in) throws IOException {
    DatumReader<GenericRecord> reader = new GenericDatumReader<GenericRecord>(avroSchema);
    Decoder decoder = DecoderFactory.get().binaryDecoder((InputStream) in, null);
    data = reader.read(null, decoder);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getJars() {

    Set<String> jars = super.getJars();
    jars.add(ClassUtils.jarForClass(GenericRecord.class));
    return jars;
  }

  private GenericRecord toAVRO(String csv) {

    String[] csvStringArray = parseCSVString(csv);

    if (csvStringArray == null) {
      return null;
    }

    if (csvStringArray.length != schema.getColumnsArray().length) {
      throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0001, "The data " + csv
          + " has the wrong number of fields.");
    }
    GenericRecord avroObject = new GenericData.Record(avroSchema);
    Column[] columnArray = schema.getColumnsArray();
    for (int i = 0; i < csvStringArray.length; i++) {
      // check for NULL field and assume this field is nullable as per the sqoop
      // schema
      if (csvStringArray[i].equals(NULL_VALUE) && columnArray[i].getNullable()) {
        avroObject.put(columnArray[i].getName(), null);
        continue;
      }
      avroObject.put(columnArray[i].getName(), toAVRO(csvStringArray[i], columnArray[i]));
    }
    return avroObject;
  }

  private Object toAVRO(String csvString, Column column) {
    Object returnValue = null;

    switch (column.getType()) {
    case ARRAY:
    case SET:
      Object[] list = toList(csvString);
      // store as a java collection
      returnValue = Arrays.asList(list);
      break;
    case MAP:
      // store as a map
      returnValue = toMap(csvString);
      break;
    case ENUM:
      returnValue = new GenericData.EnumSymbol(createEnumSchema(column), (removeQuotes(csvString)));
      break;
    case TEXT:
      returnValue = new Utf8(removeQuotes(csvString));
      break;
    case BINARY:
    case UNKNOWN:
      // avro accepts byte buffer for binary data
      returnValue = ByteBuffer.wrap(toByteArray(csvString));
      break;
    case FIXED_POINT:
      returnValue = toFixedPoint(csvString, column);
      break;
    case FLOATING_POINT:
      returnValue = toFloatingPoint(csvString, column);
      break;
    case DECIMAL:
      // TODO: store as FIXED in SQOOP-16161
      returnValue = removeQuotes(csvString);
      break;
    case DATE:
      // until 1.8 avro store as long
      returnValue = ((LocalDate) toDate(csvString, column)).toDate().getTime();
      break;
    case TIME:
      // until 1.8 avro store as long
      returnValue = ((LocalTime) toTime(csvString, column)).toDateTimeToday().getMillis();
      break;
    case DATE_TIME:
      // until 1.8 avro store as long
      returnValue = toDateTimeInMillis(csvString, column);
      break;
    case BIT:
      returnValue = Boolean.valueOf(removeQuotes(csvString));
      break;
    default:
      throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0004,
          "Column type from schema was not recognized for " + column.getType());
    }
    return returnValue;
  }

  private GenericRecord toAVRO(Object[] data) {

    if (data == null) {
      return null;
    }

    if (data.length != schema.getColumnsArray().length) {
      throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0001, "The data " + data.toString()
          + " has the wrong number of fields.");
    }
    // get avro schema from sqoop schema
    GenericRecord avroObject = new GenericData.Record(avroSchema);
    Column[] cols = schema.getColumnsArray();
    for (int i = 0; i < data.length; i++) {
      switch (cols[i].getType()) {
      case ARRAY:
      case SET:
        avroObject.put(cols[i].getName(), toList((Object[]) data[i]));
        break;
      case MAP:
        avroObject.put(cols[i].getName(), data[i]);
        break;
      case ENUM:
        GenericData.EnumSymbol enumValue = new GenericData.EnumSymbol(createEnumSchema(cols[i]), (String) data[i]);
        avroObject.put(cols[i].getName(), enumValue);
        break;
      case TEXT:
        avroObject.put(cols[i].getName(), new Utf8((String) data[i]));
        break;
      case BINARY:
      case UNKNOWN:
        avroObject.put(cols[i].getName(), ByteBuffer.wrap((byte[]) data[i]));
        break;
      case FIXED_POINT:
      case FLOATING_POINT:
        avroObject.put(cols[i].getName(), data[i]);
        break;
      case DECIMAL:
        // TODO: store as FIXED in SQOOP-16161
        avroObject.put(cols[i].getName(), ((BigDecimal) data[i]).toPlainString());
        break;
      case DATE_TIME:
        if (data[i] instanceof org.joda.time.DateTime) {
          avroObject.put(cols[i].getName(), ((org.joda.time.DateTime) data[i]).toDate().getTime());
        } else if (data[i] instanceof org.joda.time.LocalDateTime) {
          avroObject.put(cols[i].getName(), ((org.joda.time.LocalDateTime) data[i]).toDate().getTime());
        }
        break;
      case TIME:
        avroObject.put(cols[i].getName(), ((org.joda.time.LocalTime) data[i]).toDateTimeToday().getMillis());
        break;
      case DATE:
        avroObject.put(cols[i].getName(), ((org.joda.time.LocalDate) data[i]).toDate().getTime());
        break;
      case BIT:
        avroObject.put(cols[i].getName(), Boolean.valueOf((Boolean) data[i]));
        break;
      default:
        throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0001,
            "Column type from schema was not recognized for " + cols[i].getType());
      }
    }

    return avroObject;
  }

  @SuppressWarnings("unchecked")
  private String toCSV(GenericRecord record) {
    Column[] cols = this.schema.getColumnsArray();

    StringBuilder csvString = new StringBuilder();
    for (int i = 0; i < cols.length; i++) {

      Object obj = record.get(cols[i].getName());

      if (obj == null) {
        throw new SqoopException(AVROIntermediateDataFormatError.AVRO_INTERMEDIATE_DATA_FORMAT_0001, " for " + cols[i].getName());
      }

      switch (cols[i].getType()) {
      case ARRAY:
      case SET:
        List<Object> objList = (List<Object>) obj;
        csvString.append(encodeToCSVList(toObjectArray(objList), cols[i]));
        break;
      case MAP:
        Map<Object, Object> objMap = (Map<Object, Object>) obj;
        csvString.append(encodeToCSVMap(objMap, cols[i]));
        break;
      case ENUM:
      case TEXT:
        csvString.append(encodeToCSVString(obj.toString()));
        break;
      case BINARY:
      case UNKNOWN:
        csvString.append(encodeToCSVByteArray(getBytesFromByteBuffer(obj)));
        break;
      case FIXED_POINT:
        csvString.append(encodeToCSVFixedPoint(obj, cols[i]));
        break;
      case FLOATING_POINT:
        csvString.append(encodeToCSVFloatingPoint(obj, cols[i]));
        break;
      case DECIMAL:
        // stored as string
        csvString.append(encodeToCSVDecimal(obj));
        break;
      case DATE:
        // stored as long
        Long dateInMillis = (Long) obj;
        csvString.append(encodeToCSVDate(new org.joda.time.LocalDate(dateInMillis)));
        break;
      case TIME:
        // stored as long
        Long timeInMillis = (Long) obj;
        csvString.append(encodeToCSVTime(new org.joda.time.LocalTime(timeInMillis), cols[i]));
        break;
      case DATE_TIME:
        // stored as long
        Long dateTimeInMillis = (Long) obj;
        csvString.append(encodeToCSVDateTime(new org.joda.time.DateTime(dateTimeInMillis), cols[i]));
        break;
      case BIT:
        csvString.append(encodeToCSVBit(obj));
        break;
      default:
        throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0001,
            "Column type from schema was not recognized for " + cols[i].getType());
      }
      if (i < cols.length - 1) {
        csvString.append(CSV_SEPARATOR_CHARACTER);
      }

    }

    return csvString.toString();
  }

  @SuppressWarnings("unchecked")
  private Object[] toObject(GenericRecord record) {

    if (data == null) {
      return null;
    }
    Column[] cols = schema.getColumnsArray();
    Object[] object = new Object[cols.length];

    for (int i = 0; i < cols.length; i++) {
      Object obj = record.get(cols[i].getName());
      if (obj == null) {
        throw new SqoopException(AVROIntermediateDataFormatError.AVRO_INTERMEDIATE_DATA_FORMAT_0001, " for " + cols[i].getName());
      }
      Integer nameIndex = schema.getColumnNameIndex(cols[i].getName());
      Column column = cols[nameIndex];
      switch (column.getType()) {
      case ARRAY:
      case SET:
        object[nameIndex] = toObjectArray((List<Object>) obj);
        break;
      case MAP:
        object[nameIndex] = obj;
        break;
      case ENUM:
        // stored as enum symbol
      case TEXT:
        // stored as UTF8
        object[nameIndex] = obj.toString();
        break;
      case BINARY:
      case UNKNOWN:
        // stored as byte buffer
        object[nameIndex] = getBytesFromByteBuffer(obj);
        break;
      case FIXED_POINT:
      case FLOATING_POINT:
        // stored as java objects in avro as well
        object[nameIndex] = obj;
        break;
      case DECIMAL:
        // stored as string
        object[nameIndex] = obj.toString();
        break;
      case DATE:
        Long dateInMillis = (Long) obj;
        object[nameIndex] = new org.joda.time.LocalDate(dateInMillis);
        break;
      case TIME:
        Long timeInMillis = (Long) obj;
        object[nameIndex] = new org.joda.time.LocalTime(timeInMillis);
        break;
      case DATE_TIME:
        Long dateTimeInMillis = (Long) obj;
        if (((org.apache.sqoop.schema.type.DateTime) column).hasTimezone()) {
          object[nameIndex] = new org.joda.time.DateTime(dateTimeInMillis);
        } else {
          object[nameIndex] = new org.joda.time.LocalDateTime(dateTimeInMillis);
        }
        break;
      case BIT:
        object[nameIndex] = toBit(obj.toString());
        break;
      default:
        throw new SqoopException(IntermediateDataFormatError.INTERMEDIATE_DATA_FORMAT_0001,
            "Column type from schema was not recognized for " + cols[i].getType());
      }

    }
    return object;
  }
}
