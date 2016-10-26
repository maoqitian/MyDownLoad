package com.mao.mydownload;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 多线程断点续传
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener{
    private EditText edit_threadCount;
    private Button bt_download;
    private LinearLayout progress_layout;
    private Context mContext;
    private  int threadCount=0;//开启线程数
    private  int blockSize=0;//每个线程下载的大小
    private  int runningThreadCount=0;//当前运行的线程数
    private Map<Integer,ProgressBar> map=new HashMap<Integer, ProgressBar>();//用来存放每个线程的ProgressBar

    private  String path="http://172.30.163.13:8080/NewsServer/feiq.exe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext=this;
        initView();

    }

    private void initView() {
        edit_threadCount= (EditText) findViewById(R.id.edit_threadCount);
        bt_download= (Button) findViewById(R.id.dowmload);
        progress_layout = (LinearLayout) findViewById(R.id.progress_layout);
        bt_download.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        //获取用户的线程数
        String threadcount_str = edit_threadCount.getText().toString().trim();
        threadCount=Integer.parseInt(threadcount_str);
        //清除控件中的所有子控件
        progress_layout.removeAllViews();
        //根据线程数添加相应的ProgressBar
        for (int i = 0; i < threadCount; i++) {
            ProgressBar progressBar = (ProgressBar) View.inflate(mContext, R.layout.progressbar_layout, null);
            //创建一个ProgressBar就把他添加到map中，方便在线程中获取并设置进度
            map.put(i,progressBar);
            progress_layout.addView(progressBar);//子控件
        }
        //开始下载文件
        new Thread(new Runnable() {
            @Override
            public void run() {
                download();
            }
        }).start();
    }

    private void download() {
        try{
            //访问服务器获取资源
            URL url = new URL(path);
            HttpURLConnection connection=(HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10*1000);
            int code = connection.getResponseCode();
            if(code== 200){
                //1.获取资源的大小
                int filelength = connection.getContentLength();
                //2.在本地创建一个与本地资源文件同样大小的文件（占位）
                RandomAccessFile randomAccessFile=new RandomAccessFile(new File(getFileName(path)),"rw");
                randomAccessFile.setLength(filelength);

                //3.分配每个线程下载文件的开始位置和结束位置
                blockSize=filelength/threadCount;//计算出每个线程理论下载大小

                for(int threadID=0;threadID<threadCount;threadID++){
                    int startIndex=threadID*blockSize;//计算每个线程下载开始的位置
                    int endIndex=(threadID+1)*blockSize-1;//计算每个线程下载结束的位置

                    //如果是最后一个线程，结束位置需要单独计算
                    if(threadID==threadCount-1){
                        endIndex=filelength-1;
                    }
                    //4.开启线程执行下载
                    new DownLoadThread(threadID, startIndex, endIndex).start();
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //下载文件的线程逻辑
    public  class DownLoadThread extends Thread{

        private int currentThreadTotalProgress;
        private int threadID;
        private int startIndex;
        private int endIndex;
        private int lastPostion;//网络中断最后下载的位置
        //构造方法传值
        public DownLoadThread(int threadID,int startIndex,int endIndex){
            this.threadID=threadID;
            this.startIndex=startIndex;
            this.endIndex=endIndex;
            //初始化时候计算当前线程要下载的总数(也就是进度条的最大值)
            this.currentThreadTotalProgress=endIndex-startIndex+1;

        }
        public void run() {
            //获取当前线程对应的ProgressBar
            ProgressBar progressBar = map.get(threadID);


            synchronized (DownLoadThread.class) {
                runningThreadCount=runningThreadCount+1;//开启一个线程，当前线程数加1
            }
            //分段请求网络连接，分段保存文件到本地
            try {

                URL url = new URL(path);
                HttpURLConnection connection=(HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10*1000);

                System.out.println("理论上 线程："+threadID+".开始位置"+startIndex+".结束位置"+endIndex);

                //读取上次下载结束的位置.本次从这个位置直接下载
                //File file2 = new File(getFilePath()+threadID+".txt");
                if(SharedUtils.getLastPosition(mContext,threadID)!=-1){ //有文件才去获取
                  /*  BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file2)));
                    //读取这一行的字符串
                    String lastPostion_str=bufferedReader.readLine();
                    //将字符串转换成 int类型
                    lastPostion=Integer.parseInt(lastPostion_str);//读取文件上次下载的位置*/

                     lastPostion = SharedUtils.getLastPosition(mContext, threadID);

                    //断点之后继续下载，如果有的线程已经下载完成，则将其设置成最大值，而不让进度条为空
                    if(lastPostion==endIndex+1){//该线程已经下载完成
                         progressBar.setProgress(currentThreadTotalProgress);
                        runningThreadCount=runningThreadCount-1;

                    }

                    //设置分段下载的的头信息 Range:做分段请求用的头(断点续传的开始)
                    connection.setRequestProperty("Range","bytes:"+lastPostion+"-"+endIndex);//bytes:0-500 请求服务资源0-500字节之间的信息

                    System.out.println("实际上 线程："+threadID+".开始位置"+lastPostion+".结束位置"+endIndex);
                    //bufferedReader.close();
                }else{
                    //如果sharedPreferences没有文件则是第一次下载
                    lastPostion=startIndex;
                    //设置分段下载的的头信息 Range:做分段请求用的头(默认的开始)
                    connection.setRequestProperty("Range","bytes:"+lastPostion+"-"+endIndex);//bytes:0-500 请求服务资源0-500字节之间的信息

                    System.out.println("实际上 线程："+threadID+".开始位置"+lastPostion+".结束位置"+endIndex);
                }


                if(connection.getResponseCode()==206){//200请求全部资源成功，206请求部分资源成功
                    //获取请求成功的流
                    InputStream inputStream = connection.getInputStream();

                    //将请求成功的流写入之前占用的文件中
                    RandomAccessFile randomAccessFile = new RandomAccessFile(new File(getFileName(path)),"rw");
                    randomAccessFile.seek(lastPostion);//设置随机文件从哪个文件开始写入
                    //将流写入文件
                    byte[] buffer=new byte[1024*10];
                    int length=-1;
                    int total=0;//记录本次下载的位置
                    while((length=inputStream.read(buffer))>0){
                        randomAccessFile.write(buffer, 0, length);
                        total=total+length;
                        //去保存当前线程下载的位置，保存到文件中
                        int currentThreadPostion=lastPostion+total;//计算出当前线程本次下载的位置
                        /*//创建随机文件保存当前线程下载的位置
                        File file=new File(getFilePath()+threadID+".txt");
                        //将文件直接保存到硬盘中，防止出错
                        RandomAccessFile randomAccessFile2 = new RandomAccessFile(file,"rwd");
                        randomAccessFile2.write(String.valueOf(currentThreadPostion).getBytes());
                        randomAccessFile2.close();*/
                        //创建随机文件保存当前线程下载的位置到sharedPreferencesz中
                        SharedUtils.setLastPosition(mContext,threadID,currentThreadPostion);

                        //保存完文件设置进度条
                        //读取到文件，将进度设置给progress
                        int currentProgress=currentThreadPostion-startIndex;//当前下载过的减去最开始的就是正在下载的值
                        progressBar.setMax(currentThreadTotalProgress);//设置进度条的最大值
                        progressBar.setProgress(currentProgress);//设置进度条当前的进度

                    }
                    randomAccessFile.close();
                    inputStream.close();

                    System.out.println("线程："+threadID+"下载完毕");

                    //当前线程下载结束，删除存放下载位置的文件
                    synchronized (DownLoadThread.class) {
                        runningThreadCount=runningThreadCount-1;//标志着一个线程下载结束（刚开始已下载完，所以runningThreadCount会等于开启线程的总数）
                        if(runningThreadCount==0){
                            //打印一个Toast提示下载完成
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mContext,"下载完成",Toast.LENGTH_LONG).show();
                                }
                            });

                            System.out.println("所有线程下载完成");
                            for (int i = 0; i < threadCount; i++) {
                                File file=new File(getFilePath()+i+".txt");
                                file.delete();
                            }
                        }

                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            super.run();
        }
    }

    //文件保存路径截取名字
    public  String getFileName(String path){
        return Environment.getExternalStorageDirectory()+"/"+ path.substring(path.lastIndexOf("/"));//获取文件的名称：feiq.exe
    }
    //文件路径
    public String getFilePath(){
          return Environment.getExternalStorageDirectory()+"/";
    }
}
