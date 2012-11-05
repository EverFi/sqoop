/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.job.mr;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.sqoop.common.SqoopException;
import org.apache.sqoop.core.CoreError;
import org.apache.sqoop.job.JobConstants;
import org.apache.sqoop.job.PrefixContext;
import org.apache.sqoop.job.etl.Extractor;
import org.apache.sqoop.job.io.Data;
import org.apache.sqoop.job.io.DataWriter;
import org.apache.sqoop.utils.ClassUtils;

/**
 * A mapper to perform map function.
 */
public class SqoopMapper
    extends Mapper<SqoopSplit, NullWritable, Data, NullWritable> {

  public static final Log LOG =
      LogFactory.getLog(SqoopMapper.class.getName());

  @Override
  public void run(Context context) throws IOException, InterruptedException {
    Configuration conf = context.getConfiguration();

    String extractorName = conf.get(JobConstants.JOB_ETL_EXTRACTOR);
    Extractor extractor = (Extractor) ClassUtils.instantiate(extractorName);

    PrefixContext connectorContext = new PrefixContext(conf, JobConstants.PREFIX_CONNECTOR_CONTEXT);
    Object connectorConnection = ConfigurationUtils.getConnectorConnection(conf);
    Object connectorJob = ConfigurationUtils.getConnectorJob(conf);

    SqoopSplit split = context.getCurrentKey();

    try {
      extractor.run(connectorContext, connectorConnection, connectorJob, split.getPartition(),
        new MapDataWriter(context));

    } catch (Exception e) {
      throw new SqoopException(CoreError.CORE_0017, e);
    }
  }

  public class MapDataWriter extends DataWriter {
    private Context context;
    private Data data;

    public MapDataWriter(Context context) {
      this.context = context;
    }

    @Override
    public void setFieldDelimiter(char fieldDelimiter) {
      if (data == null) {
        data = new Data();
      }

      data.setFieldDelimiter(fieldDelimiter);
    }

    @Override
    public void writeArrayRecord(Object[] array) {
      writeContent(array, Data.ARRAY_RECORD);
    }

    @Override
    public void writeCsvRecord(String csv) {
      writeContent(csv, Data.CSV_RECORD);
    }

    @Override
    public void writeContent(Object content, int type) {
      if (data == null) {
        data = new Data();
      }

      data.setContent(content, type);
      try {
        context.write(data, NullWritable.get());
      } catch (Exception e) {
        throw new SqoopException(CoreError.CORE_0013, e);
      }
    }
  }

}