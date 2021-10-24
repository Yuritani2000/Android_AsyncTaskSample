package com.yuritaniapps.asynctasksample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kittinunf.fuel.core.Headers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Arrays.toString

/**
 * 今回，HTTP通信を理解するためには，以下の概念の理解が必要になる
 * ViewModel
 * Coroutine
 * 非同期処理
 *
 * 今回は，以下のライブラリを使う
 * Fuel
 *
 * 参考文献
 * 1.
 * https://developer.android.com/guide/components/processes-and-threads?hl=ja
 * 2.
 * https://codezine.jp/article/detail/13407
 * 3.
 * https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/basic-serialization.md
 */
class MainActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessTokenEditText = findViewById<EditText>(R.id.accessTokenEditText)
        val sendRequestButton = findViewById<Button>(R.id.sendRequestButton)
        val userNameEditText = findViewById<EditText>(R.id.userNameEditText)

        sendRequestButton.setOnClickListener { view ->

            // EditTextから，入力したQiitaのユーザー名とアクセストークンを受け取って，APIを呼び出すためのViewModelに渡す．
            val accessToken = accessTokenEditText.text.toString()
            val userName = userNameEditText.text.toString()

            /**
             * 自分のプロフィールを表示するために使うListViewを更新するメソッド．
             */
            val updateMyProfileListView: (MyProfile?) -> Unit = { myProfile ->
                // Kotlinはnull安全という機能が備わっている．
                // その設計思想のため，nullableな型(null値がはいるのを許容する型)とnon-nullableな型(null値が入るのを許容しない型)が存在する
                // Kotlinで用意されたメソッドでは，nullableな型を許容しないメソッドが多い．
                // nullableな変数(変数)?.let{}とすると，{}内の処理はnullableな変数がnull出なかった時のみ実行される．
                // この時{}内では，(変数)?.let{}とした(変数)のnullでないバージョンは，デフォルトで'it'という名前で使用することはできる．
                myProfile?.let { it ->
                    Log.d("debug", "received data from ViewModel: $it")
                    val myProfileArray = arrayOf(
                        it.description ?: "空欄",
                        it.facebook_id ?: "空欄",
                        it.github_login_name ?: "空欄",
                        it.id ?: "空欄",
                        it.linkedin_id ?: "空欄",
                        it.location ?: "空欄",
                        it.name ?: "空欄",
                        it.organization ?: "空欄",
                        it.profile_image_url ?: "空欄",
                        it.twitter_screen_name ?: "空欄",
                        it.website_url ?: "空欄",
                        it.followees_count.toString(),
                        it.followers_count.toString(),
                        it.items_count.toString(),
                        it.permanent_id.toString(),
                        it.team_only.toString()
                    )
                    val myProfileList = findViewById<ListView>(R.id.myProfileList)
                    val arrayAdapter = ArrayAdapter<String>(
                        this,
                        android.R.layout.simple_list_item_1,
                        myProfileArray
                    )
                    myProfileList.adapter = arrayAdapter
                }
            }

            val myProfileRepository = MyProfileRepository()
            val profileViewModel = ProfileViewModel(myProfileRepository)
            profileViewModel.getMyProfile(accessToken, userName, updateMyProfileListView)
        }

    }
}

/**
 * MyProfileRepositoryから情報を受け取るためのViewModel. ここからUIを更新するメソッドを呼び出す．
 * 今回は，Kotlin Coroutinesという仕組みを使って
 */
class ProfileViewModel(private val myProfileRepository: MyProfileRepository): ViewModel(){

    fun getMyProfile(accessToken: String, userName: String, updateMyProfileListView: (MyProfile?) -> Unit){
        var myProfileResponse: MyProfile? = null
        viewModelScope.launch(Dispatchers.Main) {
            try {
                Log.d("debug", "launch called")
                // ここで非同期処理を呼び出す…のだが，ここはUIスレッドであるため，別のスレッドを立てて行う必要がある．
                val httpResult = myProfileRepository.getMyProfileRequest(accessToken, userName)
                when(httpResult) {
                    is HttpResult.Success<MyProfile> -> {
                        myProfileResponse = httpResult.data
                        updateMyProfileListView(myProfileResponse)
                    }
                }
            }catch(e: Exception){
                // データ取得中に例外が発生した場合の処理
                Log.e("debug", e.toString())
            }
        }
    }
}

/**
 * MyProfileRepositoryのgetMyProfileRequestの返り値に使われるdata classをまとめたもの．
 * HTTPリクエストが成功した場合は，Successのクラス，失敗した場合はErrorのクラスが買えるようになっている．
 * RやTには，受け取ったJSONをデコードしたオブジェクトのMyProfileクラスを指定する．
 */
sealed class HttpResult<out R>{
    data class Success<out T>(val data: T) : HttpResult<T>()
    data class Error(val exception: Exception) : HttpResult<Nothing>()
}

/**
 * 実際に別スレッドを起動してHTTPリクエストを送信するクラス．onCreate上でオブジェクトとして取得し，それをViewModelに渡す事で使用する．
 */
class MyProfileRepository(){

    /**
     * 実際にFuelライブラリを用いてHTTPリクエストを行うメソッド．
     */
    suspend fun getMyProfileRequest(accessToken: String, userName: String): HttpResult<MyProfile>{
        // Qiita APIのエンドポイントにアクセスする際に使用するURL. Qiitaのアカウントを取得しておく必要がある．
        val URL = "https://qiita.com/api/v2/users/$userName"

        Log.d("debug", "URL: $URL");
        var resultStr = "no data"
        var httpResult: HttpResult<MyProfile> = HttpResult.Error(Exception("No http request did not succeeded."))

        // withContext(Dispatchers.IO){}の括弧内は，別スレッドで実行される．
        // Androidでは，HTTPリクエストなど外部とのデータのやり取りはUIスレッド上で行ってはならず，このように別スレッドで行なわなければならない．
        // その理由は，HTTPリクエストなど返事に時間がかかり，その間スレッドをブロックする処理は，UIスレッド上で行ってはいけないという決まりがあるからである．
        withContext(Dispatchers.IO){
            // ここに，Fuelを用いてHTTP通信を行う処理を記述する．
            Log.d("debug", "withContext called")

            // String型の変数に対してhttpGetメソッドを用いることで，GETメソッドを実行することができる．
            // この際，Qiita APIは，アカウント管理ページで作成したBearerトークンをパラメータとしてHTTPのAuthorizationヘッダに付与する必要がある．
            // このメソッドは，スレッドをブロックする方法で書かれている(別途スレッドをブロックしない方法での書き方もFuelには用意してある).
            val (request, response, result) = URL.httpGet()
            .header(Headers.AUTHORIZATION, "Bearer $accessToken")
            .responseString()

            when(result){
                is Result.Failure -> {
                    val ex = result.getException()
                    Log.e("http exception", ex.toString())
                    httpResult = HttpResult.Error(Exception(ex.toString()))
                }
                is Result.Success -> {
                    resultStr = result.value
                    Log.d("debug", "resultStr: $resultStr");
                    try {
                        val objectData = Json.decodeFromString<MyProfile>(resultStr)
                        Log.d("debug", "result converted to object: ${objectData}")
                        httpResult = HttpResult.Success(objectData)
                    }catch(e: Exception){
                        Log.e("debug" , e.toString())
                        httpResult = HttpResult.Error(e)
                    }
                }
                else ->{
                    Log.d("debug", "something went wrong")
                    httpResult = HttpResult.Error(Exception("Something went wrong."))
                }
            }
        }
        return httpResult
    }
}

@Serializable
data class MyProfile
    (
    @SerialName("description") val description: String?,
    @SerialName("facebook_id") val facebook_id: String?,
    @SerialName("followees_count") val followees_count: Int,
    @SerialName("followers_count") val followers_count: Int,
    @SerialName("github_login_name") val github_login_name: String?,
    @SerialName("id") val id: String?,
    @SerialName("items_count") val items_count: Int,
    @SerialName("linkedin_id") val linkedin_id: String?,
    @SerialName("location") val location: String?,
    @SerialName("name") val name: String?,
    @SerialName("organization") val organization: String?,
    @SerialName("permanent_id") val permanent_id: Int,
    @SerialName("profile_image_url") val profile_image_url: String?,
    @SerialName("team_only") val team_only: Boolean,
    @SerialName("twitter_screen_name") val twitter_screen_name: String?,
    @SerialName("website_url") val website_url: String?,
    )