package com.example.practiceopenweatherapi;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.HandlerCompat;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ログ用文字列
    private static final String DEBUG_TAG = "AsyncTest";

    // OpenWeatherのURL
    private static final String WEATHERINFO_URL = "https://api.openweathermap.org/data/2.5/weather?lang=ja";
    
    // OpenWeatherAPIにアクセスするためのAPI Key
    // TODO: 2020/12/29 実行前に自分用のAPIkeyを格納
    private static final String APP_ID = "";
    
    // リストビューに表示するデータのリスト
    private List<Map<String, String>> viewlist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewlist = createList();

        ListView lvCityList = findViewById(R.id.lvCityList);
        String[] from = {"name"};
        int[] to = {android.R.id.text1};
        SimpleAdapter adapter =
                new SimpleAdapter(getApplicationContext(), viewlist,
                        android.R.layout.simple_expandable_list_item_1,
                        from, to);
        lvCityList.setAdapter(adapter);
        lvCityList.setOnItemClickListener(new ListItemClickListener());
    }

    /**
     * リストビュー表示用データ作成
     * @return 表示用リストデータ
     */
    private List<Map<String, String>> createList(){
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        String[] cityName =
                {"大阪", "神戸", "京都", "大津", "奈良", "和歌山", "姫路", "幸手"};
        String[] cityNameRoma =
                {"Osaka", "Kobe", "Kyoto", "Otsu", "Nara", "Wakayama", "Himeji", "Satte"};
        for (int i = 0; i < cityName.length; i++) {
            map.put("name", cityName[i]);
            map.put("q", cityNameRoma[i]);
            list.add(map);
            map = new HashMap<>();
        }
        return list;
    }

    /**
     * リストがタップされた時の処理を記述したリスナクラス
     */
    private class ListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){
            /*
             タップされた、リストの添え字を取得(リストビューの上から、0で始まる)
             →createList()にて作成した、各リストのMapを取得する
             */
            Map<String, String> item = viewlist.get(position);
            // MapからAPI呼び出しで使用する、都市名のローマ字表記を取得する
            String q = item.get("q");
            // WebAPI呼び出し用URLの作成
            String url = WEATHERINFO_URL + "&q=" + q + "&appid=" + APP_ID;
            // 天気情報取得処理の呼び出し
            asyncExecute(url);
        }
    }

    /**
     * 天気情報の取得処理を行うメソッド
     * @param url 天気情報を取得するためのURL
     */
    @UiThread
    public void asyncExecute(final String url) {
        /*
         Handlerオブジェクトへ渡す用に、Looperオブジェクトを生成
         →この処理をUIスレッドで行うことで、現在のUIスレッドを、変数mainLooperに記憶させる
         */
        Looper mainLooper = Looper.getMainLooper();
        /*
         スレッド間通信を行うために、Handlerオブジェクトを生成
         元となるスレッド(今回はUIスレッド)で事前に用意
         かつ、HandlerCompatクラスのcreateAsyncメソッドの引数に、UIスレッドを記憶させた
         Looperオブジェクトを渡すことで、戻り先のスレッドをUIスレッドとする
         */
        Handler handler = HandlerCompat.createAsync(mainLooper);
        BackgroundTask backgroundTask = new BackgroundTask(handler, url);
        /*
        ExecutorServiceクラスのインスタンス生成
        newSingleThreadExecutor()→単純に別スレッドで動作するインスタンスを生成
         */
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        /*
        submitメソッドにより、別スレッドで処理を実行(=非同期処理)
        引数として、非同期で実行したい処理を記述した、Runnable実装クラスのインスタンスを渡す
        このsubmitによって、非同期で処理されるのは、引数として渡されるRunnable実装クラスの
        run()メソッドに記述した処理
         */
        executorService.submit(backgroundTask);
    }

    /**
     * 非同期で天気情報APIにアクセスするためのクラス
     */
    private class BackgroundTask implements Runnable {
        // 各スレッドから書き換えが出来ないよう、final宣言
        // UIスレッドを表すハンドラオブジェクト
        private final Handler _handler;
        // OpenWeatherのURL格納用
        private final String _url;

        /**
         * コンストラクタ
         * @param handler UIスレッドを表すハンドラオブジェクト
         * @param url URL
         */
        public BackgroundTask(Handler handler, String url){
            _handler = handler;
            _url = url;
        }

        @WorkerThread
        @Override
        public void run(){
            HttpURLConnection con = null;
            InputStream is = null;
            String result = "";

            try {
                // 接続先のURL文字列を元に、URLクラスのインスタンスを生成
                URL url = new URL(_url);
                // URLオブジェクトから、HttpURLConnectionオブジェクトを取得
                con = (HttpURLConnection) url.openConnection();
                // Http通信をGETに指定
                con.setRequestMethod("GET");
                /*
                GETで指定URLへ接続
                →この時、レスポンスデータの取得も行われている為、HttpURLConnectionオブジェクト内部(con)には
                レスポンスデータが格納されている
                 */
                con.connect();
                /*
                getInputStream()でレスポンスデータの取得
                 */
                is = con.getInputStream();
                // レスポンスデータをStringへ変換
                result = is2String(is);
            } catch (MalformedURLException ex) {
                Log.e(DEBUG_TAG, "URL変換失敗", ex);
            } catch (IOException ex) {
                Log.e(DEBUG_TAG, "通信失敗", ex);
            } finally {
                if (con != null) {
                    // HttpURLConnectionオブジェクトの解放
                    con.disconnect();
                }
                if (is != null) {
                    try {
                        // InputStreamオブジェクトの解放
                        is.close();
                    } catch (IOException ex) {
                        Log.e(DEBUG_TAG, "InputStream解放失敗", ex);
                    }
                }
            }
            PostExecutor postExecutor = new PostExecutor(result);
            /*
            UIスレッドを表すHandlerオブジェクトにて、postメソッド実行時に、
            レスポンスデータを格納した、Runnable実装クラスのインスタンスを、引数として渡す事で、
            非同期処理終了後に、UIスレッドで実行する処理を記述した、PostExecutorクラスのrunメソッドが実行される。
             */
            _handler.post(postExecutor);
        }

        /**
         * InputStreamオブジェクトを文字列に変換するメソッド
         * @param is 変換対象のInputStreamオブジェクト
         * @return 変換された文字列
         * @throws IOException 変換に失敗した際に投げる
         */
        private String is2String(InputStream is) throws IOException {
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuffer sb = new StringBuffer();
            char[] b = new char[1024];
            int line;
            while (0 <= (line = reader.read(b))){
                sb.append(b, 0, line);
            }
            return sb.toString();
        }
    }

    /**
     * 非同期で天気情報を取得した後に、UIスレッドでその情報を表示するためのクラス
     */
    private class PostExecutor implements Runnable {
        // 取得した天気情報のJSON文字列格納用
        private final String _result;

        /**
         * コンストラクタ
         * @param result WebAPIから取得した天気情報のJSON文字列
         */
        public PostExecutor(String result){
            _result = result;
        }

        @UiThread
        @Override
        public void run(){
            // バックグラウンド処理終了後に、UIスレッドで実行する処理
            String cityName = "";
            String weather = "";
            try {
                /*
                String型にしたレスポンスデータを引数にして、JSONObjectクラスのインスタンス生成
                →この際に、引数として渡したレスポンスデータが、JSONObjectクラスのクラス変数
                「nameValuePairs」に、JSON形式としてのkey-valueのセットで格納される。
                 */
                JSONObject rootJSON = new JSONObject(_result);
                /*
                都市名を取得
                →都市名のkeyに指定されている"name"を指定
                 */
                cityName = rootJSON.getString("name");
                // 天気情報が格納されてるJSONArrayオブジェクトを取得
                JSONArray weatherJSONArray = rootJSON.getJSONArray("weather");
                // JSONArrayオブジェクトのindex:0に格納されているJSONObjectのオブジェクトを取得
                JSONObject weatherJSON = weatherJSONArray.getJSONObject(0);
                // 天気の詳細を取得
                weather = weatherJSON.getString("description");
            } catch (JSONException ex) {
                Log.e(DEBUG_TAG, "JSON解析失敗", ex);
            }
            String telop = cityName + "の天気";
            String desc = "現在は" + weather + "です。";
            TextView tvWeatherTelop = findViewById(R.id.tvWeatherTelop);
            TextView tvWeatherDesc = findViewById(R.id.tvWeatherDesc);
            tvWeatherTelop.setText(telop);
            tvWeatherDesc.setText(desc);
        }
    }
}