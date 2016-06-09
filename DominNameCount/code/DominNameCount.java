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

import java.io.IOException;
import java.util.StringTokenizer;
import java.io.DataInput;
import java.io.DataOutput;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class DominNameCount {

  public static class DominName implements WritableComparable{
    private Text word;
    private String[] domin;
    private String dominname;
    private int level;

    public DominName(){
      word = new Text();
      level = 0;
      domin = new String[5];
      dominname = new String();
    }

    public DominName(String s){
      dominname = s;
      domin = new String[5];
      int start = 0;
      for(int i = 0; i <= s.length(); ++i){
        if(s.charAt(i) == '.' || i == s.length())
        {
          domin[level] = s.substring(start,i);
          start = i+1;
          level++;
        }
      }
    }

    public void set(String s){
      dominname = s;
      level = 0;
      int start = 0;
      for(int i = 0; i <= s.length(); ++i){
        if(i == s.length() || (s.charAt(i) == '.' && i != 0))
        {
          domin[level] = s.substring(start,i);
          start = i+1;
          level++;
        }
      }
    }

    public void readFields(DataInput in) throws IOException {
      ;
    }

    public void write(DataOutput out) throws IOException {
      word.set(dominname);
      word.write(out);
    }

    public int getLevel(){
      return level;
    }

    public String getDomin(int i){
      if(i >= level || i < 0) return null;
      return domin[i];
    }

    public String toString(){
      return dominname;
    }

    public int compareTo(Object o) {
      DominName d = (DominName)o;

      if(this.getLevel() > d.getLevel()) return 1;
      else if(this.getLevel() < d.getLevel()) return -1;

      for(int i = this.getLevel() - 1; i >= 0; i--)
      {
        if(!this.getDomin(i).equals(d.getDomin(i)))
           return this.getDomin(i).compareTo(d.getDomin(i));
      }
      return 0;
    }
  }

  public static class GetDominNameMapper
       extends Mapper<Object, Text, DominName, IntWritable>{

    private final static IntWritable one = new IntWritable(1);
    private DominName dominName = new DominName();
    private Text word = new Text();
    private int start;

    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      String s = value.toString();
      start = 0;
      while(true)
      {
        if(!findDomin(s)) break;
        int start_d = start;
        boolean finish = false;
        while(true)
        {
          String d = getDomin(start_d,s);
          dominName.set(d);
          //word.set(dominName.toString());
          //word.set(d);
          //context.write(word,one);
          context.write(dominName,one);
          if(finish) break;
          start_d--;
          for(; start_d >= 0; --start_d)
          {
            if(s.charAt(start_d) == '.'){
              break;
            }
            if(!(s.charAt(start_d) >= 'a' && s.charAt(start_d) <= 'z')){
              finish = true;
              start_d++;
              break;
            }
          }
        }
      }
    }

    private boolean findDomin(String s){
      start++;
      for(;start < s.length() - 5; ++start)
      {
        if(check(start,s))
          return true;
      }
      return false;
    }

    private boolean check(int index, String s){
      String s1 = s.substring(index,index+4);
      String s2 = s.substring(index,index+3);
      if(s1.equals(".com") || s1.equals(".org") || s1.equals(".net")) return true;
      if(s2.equals(".cn")) return true;

      return false;
    }

    private String getDomin(int index, String s){
      int end;
      for(end = index; end < s.length(); ++end){
        if(!((s.charAt(end) >= 'a' && s.charAt(end) <= 'z') || s.charAt(end) == '.')) break;
      }
      return s.substring(index,end);
    }
  }

  public static class IntSumReducer
       extends Reducer<DominName,IntWritable,DominName,IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(DominName key, Iterable<IntWritable> values,
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length < 2) {
      System.err.println("Usage: wordcount <in> [<in>...] <out>");
      System.exit(2);
    }
    Job job = new Job(conf, "word count");
    job.setJarByClass(DominNameCount.class);
    job.setMapperClass(GetDominNameMapper.class);
    job.setCombinerClass(IntSumReducer.class);
    job.setReducerClass(IntSumReducer.class);
    job.setOutputKeyClass(DominName.class);
    job.setOutputValueClass(IntWritable.class);
    for (int i = 0; i < otherArgs.length - 1; ++i) {
      FileInputFormat.addInputPath(job, new Path(otherArgs[i]));
    }
    FileOutputFormat.setOutputPath(job,
      new Path(otherArgs[otherArgs.length - 1]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}
