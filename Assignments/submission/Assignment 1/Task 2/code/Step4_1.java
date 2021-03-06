package Task2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

// First part of matrix multiplication, getting all the products and grouping the ones that need to be added together

public class Step4_1 {
    public static class Step4_PartialMultiplyMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text k = new Text();
        private Text v = new Text();

        @Override
        public void map(LongWritable key, Text values, Context context) throws IOException, InterruptedException {

            // input is of two possible forms:
            // 1. < "itemA", iterable("itemB:cooccurrence_count,itemC:cooccurrence_count,...") >
            // 2. < "itemID", iterable("userID_user:score,userID_user:score,...") >
            // output to reducer is of two possible forms:
            // 1. < "itemID", "userID_user:score" >
            // 2. < "itemA", "itemB:cooccurrence_count" >

            String[] key_value = Recommend.TAB_DELIMITER.split(values.toString());
            k.set(key_value[0]);
            String[] tokens = Recommend.DELIMITER.split(key_value[1]);
            for (String token: tokens) {
                v.set(token);
                context.write(k, v);
            }
        }
    }

    public static class Step4_AggregateReducer extends Reducer<Text, Text, Text, Text> {
        private Text k = new Text();
        private Text v = new Text();

        @Override
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            // input is of two possible forms:
            // 1. < "itemID", "userID_user:score" >
            // 2. < "itemA", "itemB:cooccurrence_count" >
            // output is of the form < "userID,itemB,itemID", "score*cooccurrence_count" >

            ArrayList<String> scores_by_users = new ArrayList<String>();
            ArrayList<String> counts_by_items = new ArrayList<String>();
            for (Text value: values) {
                if (value.toString().indexOf("_user") != -1) {
                    scores_by_users.add(value.toString());
                }
                else {
                    counts_by_items.add(value.toString());
                }
            }
            String userID, itemID;
            int count;
            float score, product;
            for (String user_score: scores_by_users) {
                userID = user_score.substring(0, user_score.indexOf("_user"));
                score = Float.parseFloat(user_score.substring(user_score.indexOf(":") + 1));
                for (String item_count: counts_by_items) {
                    itemID = item_count.substring(0, item_count.indexOf(":"));
                    count = Integer.parseInt(item_count.substring(item_count.indexOf(":") + 1));
                    product = score * count;
                    k.set(userID + "," + itemID + "," + key.toString());
                    v.set(Float.toString(product));
                    context.write(k, v);
                }
            }
        }
    }

    public static void run(Map<String, String> path) throws IOException, InterruptedException, ClassNotFoundException {
        //get configuration info
        Configuration conf = Recommend.config();
        // get I/O path
        Path input1 = new Path(path.get("Step4_1Input1"));
        Path input2 = new Path(path.get("Step4_1Input2"));
        Path output = new Path(path.get("Step4_1Output"));
        // delete last saved output
        HDFSAPI hdfs = new HDFSAPI(new Path(Recommend.HDFS));
        hdfs.delFile(output);
        // set job
        Job job = Job.getInstance(conf);
        job.setJarByClass(Step4_1.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setMapperClass(Step4_1.Step4_PartialMultiplyMapper.class);
        job.setReducerClass(Step4_1.Step4_AggregateReducer.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        FileInputFormat.setInputPaths(job, input1, input2);
        FileOutputFormat.setOutputPath(job, output);

        job.waitForCompletion(true);
    }
}