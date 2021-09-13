package com.yuritaniapps.asynctasksample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
 *
 */
class MainActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessTokenEditText = findViewById<EditText>(R.id.accessTokenEditText)
        val sendRequestButton = findViewById<Button>(R.id.sendRequestButton)
        val resultTextView = findViewById<TextView>(R.id.resultTextView)

        sendRequestButton.setOnClickListener { view ->
            val accessToken = accessTokenEditText.text.toString()

            val URL = "https://qiita.com/api/v2/users/Yuritani"
            var newText = "data empty"

            val myProfileRepository = MyProfileRepository()
            val profileViewModel = ProfileViewModel(myProfileRepository, resultTextView)
            val profileStr = profileViewModel.getMyProfile(accessToken)
            resultTextView.text = profileStr
        }

    }
}

/**
 *
 */
class ProfileViewModel(private val myProfileRepository: MyProfileRepository, private val textView: TextView): ViewModel(){

    fun getMyProfile(accessToken: String): String{
        var response = "no response"
        viewModelScope.launch(Dispatchers.Main) {
            try {
                Log.d("debug", "launch called")
                // ここで非同期処理を呼び出す…のだが，ここはUIスレッドであるため，別のスレッドを立てて行う必要がある．
                response = myProfileRepository.getMyProfileRequest(accessToken)
//                Log.d("debug", "response has been returned $response")
                textView.text = response
            }catch(e: Exception){
                // データ取得中に例外が発生した場合の処理
            }
        }
        return response
    }
}

// RやTが何かわからない．レスポンスのモデル化とは何かわからない．data classの意味が分からない．
//sealed class Result<out R>{
//    data class Success<out T>(val data: T) : Result<T>()
//    data class Error(val exception: Exception) : Result<Nothing>()
//}

/**
 * 実際に別スレッドを起動してHTTPリクエストを送信するクラス．onCreate上でオブジェクトとして取得し，それをViewModelに渡す事で使用する．
 */
class MyProfileRepository(){

    suspend fun getMyProfileRequest(accessToken: String): String{
        val URL = "https://qiita.com/api/v2/users/Yuritani"
        var resultStr = "no data"
        withContext(Dispatchers.IO){
            // ここに，Fuelを用いてHTTP通信を行う処理を記述する．
            Log.d("debug", "withContext called")
            val (request, response, result) = URL.httpGet()
            .header(Headers.AUTHORIZATION, "Bearer $accessToken")
            .responseString()
            when(result){
                is Result.Failure -> {
                    val ex = result.getException()
                    Log.e("http exception", ex.toString())
                }
                is Result.Success -> {
                    resultStr = result.value
                    try {
                        val objectData = Json.decodeFromString<MyProfile>(resultStr)
                        Log.d("debug", "result converted to object: ${objectData}")
                    }catch(e: Exception){
                        Log.e("error" , e.toString())
                    }
                }
                else ->{
                    Log.d("debug", "something went wrong")
                }
            }
        }
        return resultStr
    }
}

@Serializable
data class MyProfile
    (
    @SerialName("description") val description: String? = "",
    @SerialName("facebook_id") val facebook_id: String? = "",
    @SerialName("followees_count") val followees_count: Int,
    @SerialName("followers_count") val followers_count: Int,
    @SerialName("github_login_name") val github_login_name: String? = "",
    @SerialName("id") val id: String? = "",
    @SerialName("items_count") val items_count: Int,
    @SerialName("linkedin_id") val linkedin_id: String? = "",
    @SerialName("location") val location: Int,
    @SerialName("name") val name: String? = "",
    @SerialName("organization") val organization: String? = "",
    @SerialName("permanent_id") val permanent_id: Int,
    @SerialName("profile_image_url") val profile_image_url: String? = "",
    @SerialName("team_only") val team_only: Boolean,
    @SerialName("twitter_screen_name") val twitter_screen_name: String? = "",
    @SerialName("website_url") val website_url: String? = "",
    )