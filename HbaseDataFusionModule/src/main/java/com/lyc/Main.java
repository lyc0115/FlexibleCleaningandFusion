package com.lyc;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.Mutation;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.mapreduce.TableReducer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @ProjectName FlexibleCleaningAndFusion
 * @ClassName Main
 * @Description TODO
 * @Author lyc
 * @Date 2021/7/30 9:26
 **/
public class Main extends Configured implements Tool {

    public static class MyMapper extends TableMapper<ImmutableBytesWritable, ImmutableBytesWritable> {

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            String configFilePath = conf.get("configFilePath");
            String fusionTableA = conf.get("fusionTableA");
            String fusionTableB = conf.get("fusionTableB");
            String resultTable = conf.get("resultTable");
            ConfigInit.initIndexRule(configFilePath,fusionTableA,fusionTableB,resultTable);

        }

        @Override
        protected void map(ImmutableBytesWritable row, Result values, Context context) throws IOException,
                InterruptedException {
            //????????????
            String rowkey = Bytes.toString(row.get());
            //??????????????????????????????
            String qualifier = "";
            //????????????
            String cellValue = "";
            //??????????????????
            String baseValue = "";
            //??????????????????
            String compareValue = "";
            //???????????????????????????
            String valuestr = "";
            //??????????????????????????????
            String value = "";
            //????????????rowkey
            String newRowKey = "";

            //??????????????????????????????????????????????????????????????????????????????reduce??????????????????
            if (rowkey.contains(ConfigInit.fusionTableName_A)) {
                Cell[] rawCell = values.rawCells();
                for (Cell cell : rawCell) {
                    qualifier = Bytes.toString(CellUtil.cloneQualifier(cell));
                    cellValue = Bytes.toString(CellUtil.cloneValue(cell));
                    //????????????????????????????????????
                    if (qualifier.equals(ConfigInit.tableA_ParameterA)) {
                        baseValue = cellValue;
                    } else if (qualifier.equals(ConfigInit.tableA_ParameterB)){
                        compareValue = cellValue;
                    }

                    //??????reduce????????????????????????????????????????????????????????????
                    if (cellValue.length() <= 0) {
                        cellValue = " ";
                    }
                    //??????????????????????????????????????????<=>????????????????????????
                    valuestr = qualifier + "<=>" + cellValue;
                    //?????????????????????????????????????????????????????????????????????reduce???????????????????????????????????????<->????????????????????????????????????????????????
                    value += (value.length() > 0 ? "<->" : ConfigInit.fusionTableName_A + "<=>") + valuestr;
                }
                System.out.println("????????????" + value);

                //??????????????????
                WriteData(context, baseValue, compareValue, value, ConfigInit.tableA_ParameterA);
            }else if (rowkey.contains(ConfigInit.fusionTableName_B)) {
                Cell[] rawCell = values.rawCells();
                for (Cell cell : rawCell) {
                    qualifier = Bytes.toString(CellUtil.cloneQualifier(cell));
                    cellValue = Bytes.toString(CellUtil.cloneValue(cell));

                    if (qualifier.equals(ConfigInit.tableB_ParameterA)) {
                        baseValue = cellValue;
                    }else if (qualifier.equals(ConfigInit.tableB_ParameterB)) {
                        compareValue = cellValue;
                    }

                    //????????????????????????????????????
                    if (ConfigInit.extractParameters.contains(qualifier)) {
                        //??????reduce????????????????????????????????????????????????????????????
                        if (cellValue.length() <= 0) {
                            cellValue = " ";
                        }
                        valuestr = qualifier + "<=>" + cellValue;
                        value += (value.length() > 0 ? "<->" : ConfigInit.fusionTableName_B + "<=>") + valuestr;
                    }
                }

                System.out.println("????????????" + value);
                WriteData(context, baseValue, compareValue, value, ConfigInit.tableB_ParameterA);

            }
        }

        private void WriteData(Mapper<ImmutableBytesWritable, Result, ImmutableBytesWritable, ImmutableBytesWritable>.Context context, String baseValue, String compareValue, String value, String table_parameterA) throws IOException, InterruptedException {
            String newRowKey;//??????????????????
            if (table_parameterA.length() == 0) {
                newRowKey = compareValue;
            } else {
                newRowKey = baseValue + ":" + compareValue;
            }

            ImmutableBytesWritable keyv =
                    new ImmutableBytesWritable(Bytes.toBytes(newRowKey));
            ImmutableBytesWritable val = new ImmutableBytesWritable(Bytes.toBytes(value));
            context.write(keyv, val);
        }
    }

    public static class MyReducer extends TableReducer<ImmutableBytesWritable, ImmutableBytesWritable, ImmutableBytesWritable> {

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
            Configuration conf = context.getConfiguration();
            String configFilePath = conf.get("configFilePath");
            String fusionTableA = conf.get("fusionTableA");
            String fusionTableB = conf.get("fusionTableB");
            String resultTable = conf.get("resultTable");
            ConfigInit.initIndexRule(configFilePath,fusionTableA,fusionTableB,resultTable);
        }

        @Override
        protected void reduce(ImmutableBytesWritable key, Iterable<ImmutableBytesWritable> values, Context context) throws IOException, InterruptedException {

            String rowkey = Bytes.toString(key.get());
            //?????????????????????
            ArrayList<String> fusionTableA = new ArrayList<>();
            ArrayList<String> fusionTableB = new ArrayList<>();
            //????????????
            String qualifier = "";
            //????????????
            String cellvalue = "";
            HashMap<String, String> valueMap = new HashMap<>();

            //?????????????????????????????????????????????????????????
            for (ImmutableBytesWritable value : values) {
                //???????????????
                String tableFlag = Bytes.toString(value.get()).split("<=>")[0];
                if (tableFlag.equals(ConfigInit.fusionTableName_A)) {
                    fusionTableA.add(Bytes.toString(value.get()).replace(tableFlag + "<=>", ""));
                }else if (tableFlag.equals(ConfigInit.fusionTableName_B)){
                    fusionTableB.add(Bytes.toString(value.get()).replace(tableFlag + "<=>", ""));
                }
            }

            if (!fusionTableB.isEmpty()) {
                String fusionValue = "";
                for (String rawcell : fusionTableB) {
                    String[] cells = rawcell.split("<->");
                    for (String cell : cells) {
                        qualifier = cell.split("<=>")[0];
                        cellvalue = cell.split("<=>")[1];
                        //???????????????????????????????????????????????????
                        if (qualifier.equals(ConfigInit.tableAAddRelParameter)) {
                            fusionValue += (fusionValue.length() > 0 ? ";" : "") + cellvalue;
                        }
                        //?????????????????????????????????????????????
                        valueMap.put(ConfigInit.oldToNewMap.get(qualifier), cellvalue);
                    }
                }
                //?????????????????????
                valueMap.put(ConfigInit.tableAAddRelParameter, fusionValue);
                //????????????
                WriteData(context, valueMap, rowkey, fusionTableA);
            }else {
                WriteData(context, valueMap, rowkey, fusionTableA);
            }

        }

        public void WriteData(Reducer<ImmutableBytesWritable, ImmutableBytesWritable, ImmutableBytesWritable, Mutation>.Context context, HashMap<String, String> valueMap, String rowkey, ArrayList<String> fusionTableA) throws IOException, InterruptedException {
            String qualifier = "";
            String cellvalue = "";
            for (String rawcell : fusionTableA) {
                String[] cells = rawcell.split("<->");
                for (String cell : cells) {
                    qualifier = cell.split("<=>")[0];
                    cellvalue = cell.split("<=>")[1];
                    valueMap.put(qualifier, cellvalue);
                }
            }
            String newRowKey = ConfigInit.resultTable + "<=>" + rowkey;
            //????????????
            Put put = new Put(Bytes.toBytes(newRowKey));
            ImmutableBytesWritable key = new ImmutableBytesWritable(Bytes.toBytes(newRowKey));
            //?????????????????????
            for (Map.Entry<String, String> entry : valueMap.entrySet()) {
                put.addColumn(Bytes.toBytes("info"), Bytes.toBytes(entry.getKey()), Bytes.toBytes(entry.getValue()));
                context.write(key, put);
            }
        }
    }


    @Override
    public int run(String[] args) throws Exception {

        boolean b = true;

        String configFilePath = args[0];
        ArrayList<String> tables = ReadConfigFile.ReadConfigItem(configFilePath, "??????Hbase??????=");

        for (String table : tables) {

            String fusionTableA = table.split("<=>")[0];
            String fusionTableB = table.split("<=>")[1];
            String resultTable = table.split("<=>")[2];
            System.out.println("?????????A(??????)???" + fusionTableA);
            System.out.println("?????????B(??????)???" + fusionTableB);
            System.out.println("????????????" + resultTable);

            Configuration hbaseConf = HBaseConfiguration.create();
            hbaseConf.set("configFilePath", configFilePath);
            hbaseConf.set("fusionTableA", fusionTableA);
            hbaseConf.set("fusionTableB",fusionTableB);
            hbaseConf.set("resultTable",resultTable);

            Job job = Job.getInstance(hbaseConf, "fusion_"+fusionTableA + "_" + fusionTableB);
            job.setJarByClass(Main.class);

            ConfigInit.initIndexRule(configFilePath,fusionTableA,fusionTableB,resultTable);

            List<Scan> scans = new ArrayList<>();
            Scan scan1 = new Scan();
            scan1.setCaching(500);
            scan1.setCacheBlocks(false);
            scan1.setAttribute(Scan.SCAN_ATTRIBUTES_TABLE_NAME, Bytes.toBytes(fusionTableA));
            scans.add(scan1);

            Scan scan2 = new Scan();
            scan2.setCaching(500);
            scan2.setCacheBlocks(false);
            scan2.setAttribute(Scan.SCAN_ATTRIBUTES_TABLE_NAME, Bytes.toBytes(fusionTableB));
            scans.add(scan2);

            TableMapReduceUtil.initTableMapperJob(scans, MyMapper.class, ImmutableBytesWritable.class, ImmutableBytesWritable.class, job);
            TableMapReduceUtil.initTableReducerJob(resultTable, MyReducer.class, job);

            b = job.waitForCompletion(true);


        }
        return b ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        //??????HBaseConfiguration??????
        Configuration configuration = HBaseConfiguration.create();
        int run = ToolRunner.run(configuration, new Main(), args);
        System.exit(run);
    }
}
