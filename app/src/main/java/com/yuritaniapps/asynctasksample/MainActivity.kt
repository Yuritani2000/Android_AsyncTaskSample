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
 *
 */
class MainActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val accessTokenEditText = findViewById<EditText>(R.id.accessTokenEditText)
        val sendRequestButton = findViewById<Button>(R.id.sendRequestButton)
//        val resultTextView = findViewById<TextView>(R.id.resultTextView)


        sendRequestButton.setOnClickListener { view ->
            val accessToken = accessTokenEditText.text.toString()

            val URL = "https://qiita.com/api/v2/users/Yuritani"
            var newText = "data empty"

            val myProfileRepository = MyProfileRepository()
            val profileViewModel = ProfileViewModel(myProfileRepository)
            val myProfile = profileViewModel.getMyProfile(accessToken)
            myProfile?.let{ it ->
                val myProfileFields = MyProfile::class.java.declaredFields.mapNotNull { it.name }
                myProfileFields.forEach{
                    )
                }

                val myProfileList = findViewById<ListView>(R.id.myProfileList)
                val arrayAdapter = ArrayAdapter<Map<String, String>>(this, myProfileMap, android.R.layout.simple_list_item_2, arrayOf(android.R.id.text1, android.R.id.text2))

            }
        }

    }
}

/**
 *
 */
class ProfileViewModel(private val myProfileRepository: MyProfileRepository): ViewModel(){

    fun getMyProfile(accessToken: String): MyProfile?{
        var response: MyProfile? = null
        viewModelScope.launch(Dispatchers.Main) {
            try {
                Log.d("debug", "launch called")
                // ここで非同期処理を呼び出す…のだが，ここはUIスレッドであるため，別のスレッドを立てて行う必要がある．
                val httpResult = myProfileRepository.getMyProfileRequest(accessToken)
                when(httpResult) {
                    is HttpResult.Success<MyProfile> -> {
                        response = httpResult.data
                    }
                }
//                Log.d("debug", "response has been returned $response")
            }catch(e: Exception){
                // データ取得中に例外が発生した場合の処理
                Log.e("debug", e.toString())
            }
        }
        return response
    }
}

//RやTが何かわからない．レスポンスのモデル化とは何かわからない．data classの意味が分からない．
sealed class HttpResult<out R>{
    data class Success<out T>(val data: T) : HttpResult<T>()
    data class Error(val exception: Exception) : HttpResult<Nothing>()
}

/**
 * 実際に別スレッドを起動してHTTPリクエストを送信するクラス．onCreate上でオブジェクトとして取得し，それをViewModelに渡す事で使用する．
 */
class MyProfileRepository(){

    suspend fun getMyProfileRequest(accessToken: String): HttpResult<MyProfile>{
        val URL = "https://qiita.com/api/v2/users/Yuritani"
        var resultStr = "no data"
        var httpResult: HttpResult<MyProfile> = HttpResult.Error(Exception("No http request did not succeeded."))
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
    @SerialName("description") val description: String,
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