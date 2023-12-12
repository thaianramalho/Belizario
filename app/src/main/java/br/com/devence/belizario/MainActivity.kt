package br.com.devence.belizario

import LocationAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.text.Normalizer
import android.content.pm.PackageManager
import android.icu.lang.UScript.ScriptUsage
import android.location.Location
import android.widget.ImageButton
import android.widget.ScrollView
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import org.w3c.dom.Text


data class LocalizacaoApi(val nome: String, val latlng: String)

class MainActivity : AppCompatActivity() {

    lateinit var mapFragment: SupportMapFragment
    lateinit var googleMap: GoogleMap
    private val markerList = mutableListOf<Marker>()
    private var markerMaisProximo: LatLng? = null
    private var distanciaMaisProxima: Float = Float.MAX_VALUE


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            obterLocalizacaoAtual()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        val autoCompleteTextView = findViewById<AutoCompleteTextView>(R.id.inputBusca)
        val inputBusca = findViewById<EditText>(R.id.inputBusca)
        val confirmBusca = findViewById<ImageButton>(R.id.confirmBusca)
        val limparInputBusca = findViewById<ImageButton>(R.id.limparInput)
        val locInicial = LatLng(-21.22332575411119, -43.77215283547053)
        val zoomLevel = 13f

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://thaianramalho.com/api_belizario/sintomas.php?senha=dxic5CyB").build()

        limparInputBusca.setOnClickListener {
            limparInput(inputBusca)
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showErrorDialog(this@MainActivity)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                responseData?.let {
                    val jsonArray = JSONArray(it)
                    val suggestions = mutableListOf<String>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val nome = obj.getString("nome")
                        suggestions.add(nome)
                    }
                    runOnUiThread {
                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_dropdown_item_1line,
                            suggestions
                        )
                        autoCompleteTextView.setAdapter(adapter)
                        autoCompleteTextView.threshold = 1
                        autoCompleteTextView.setOnItemClickListener { parent, view, position, id ->
                            val selectedItem = parent.getItemAtPosition(position) as String
                            autoCompleteTextView.setText(selectedItem, false)
                        }
                        autoCompleteTextView.addTextChangedListener(object : TextWatcher {
                            override fun afterTextChanged(s: Editable?) {}
                            override fun beforeTextChanged(
                                s: CharSequence?, start: Int, count: Int, after: Int
                            ) {
                            }

                            override fun onTextChanged(
                                s: CharSequence?, start: Int, before: Int, count: Int
                            ) {
                                adapter.filter.filter(s, object : Filter.FilterListener {
                                    override fun onFilterComplete(count: Int) {
                                        if (count == 0) {
                                            autoCompleteTextView.dismissDropDown()
                                        } else {
                                            autoCompleteTextView.showDropDown()
                                        }
                                    }
                                })
                            }
                        })
                    }
                }
            }
        })



        confirmBusca.setOnClickListener {
            val textoBusca = inputBusca.text.toString()
            markerMaisProximo = null
            distanciaMaisProxima = Float.MAX_VALUE

            for (marker in markerList) {
                marker.remove()
            }
            markerList.clear()

            runOnUiThread(this@MainActivity, textoBusca, locInicial)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        }

        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(OnMapReadyCallback {
            googleMap = it

            locInicial
            zoomLevel
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locInicial, zoomLevel))
        })

    }


    private fun obterLocalizacaoAtual() {
        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val locInicial = LatLng(location.latitude, location.longitude)
                val markerIcon =
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                googleMap.addMarker(
                    MarkerOptions().position(locInicial).title("Sua Localização").icon(markerIcon)
                )
                val zoomLevel = 14f
                zoomLevel
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        locInicial, zoomLevel
                    )
                )
            } else {
                exibirMensagemErro("Não foi possível obter a localização.")

            }
        }.addOnFailureListener { e ->
            exibirMensagemErro("Falha na obtenção da localização. Verifique as configurações de localização do dispositivo.")
        }
    }

    private fun exibirMensagemErro(mensagem: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Erro")
        builder.setMessage(mensagem)
        builder.setPositiveButton("OK", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissão concedida, obter a localização atual
                obterLocalizacaoAtual()
            } else {
                // Permissão negada, tratar conforme necessário
                // Por exemplo, exibir uma mensagem ao usuário
            }
        }
    }


    private fun msgNenhumResultadoEncontrado() {
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder.setMessage("Nenhum local encontrado.").setCancelable(false)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        val alert = dialogBuilder.create()
        alert.setTitle("Aviso")
        alert.show()
    }

    fun limparInput(input: EditText) {
        input.text.clear()
        markerList.clear()

    }

    private fun runOnUiThread(context: Context, textoBusca: String, userLocation: LatLng) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://thaianramalho.com/api_belizario/atendimento.php?senha=dxic5CyB&sintoma=${textoBusca}")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showErrorDialog(context)
            }

            @SuppressLint("CutPasteId")
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()

                if (responseData == "Nenhum resultado encontrado.") {
                    runOnUiThread {
                        msgNenhumResultadoEncontrado()
                        val inputBusca = findViewById<EditText>(R.id.inputBusca)
                        limparInput(inputBusca)
                    }
                } else {
                    responseData?.let {
                        val jsonArray = JSONArray(it)
                        val localizacoesApi =
                            Gson().fromJson(it, Array<LocalizacaoApi>::class.java).toList()


                        runOnUiThread {
                            val layout = findViewById<LinearLayout>(R.id.layoutPrincipal)
                            layout.removeAllViews()

                            for (marker in markerList) {
                                marker?.remove()
                            }
                            markerList.clear()

                            val listaLocais = findViewById<TextView>(R.id.listaLocais)
                            listaLocais?.text = ""
                            listaLocais?.visibility = View.VISIBLE

                            val outrasOpcoes = findViewById<TextView>(R.id.outrasOpcoes)
                            outrasOpcoes?.visibility = View.VISIBLE


                            localizacoesApi.forEach { localizacao ->
                                val btn = Button(this@MainActivity)
                                val params = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                params.setMargins(0, 16, 0, 0)
                                btn.layoutParams = params

                                val buttonText = "<b>${localizacao.nome}</b><br/>Latitude: ${
                                    localizacao.latlng.split(",")[0]
                                }, Longitude: ${localizacao.latlng.split(",")[1]}"
                                btn.text = HtmlCompat.fromHtml(
                                    buttonText, HtmlCompat.FROM_HTML_MODE_COMPACT
                                )
                                btn.setBackgroundResource(android.R.drawable.btn_default)
                                btn.setOnClickListener {
                                    val latLngArray = localizacao.latlng.split(",")
                                    if (latLngArray.size == 2) {
                                        val latitude = latLngArray[0].toDouble()
                                        val longitude = latLngArray[1].toDouble()
                                        val loc = LatLng(latitude, longitude)
                                        val zoomLevel = 16f
                                        googleMap.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                loc, zoomLevel
                                            )
                                        )
                                    }
                                }

                                val layout = findViewById<LinearLayout>(R.id.layoutPrincipal)

                                layout.addView(btn)
                            }


                            localizacoesApi.forEach { localizacao ->
                                val latLngArray = localizacao.latlng.split(",")
                                if (latLngArray.size == 2) {
                                    val latitude = latLngArray[0].toDouble()
                                    val longitude = latLngArray[1].toDouble()
                                    val locAtendimento = LatLng(latitude, longitude)

                                    val distancia = calcularDistancia(userLocation, locAtendimento)

                                    if (markerMaisProximo == null || distancia < distanciaMaisProxima) {
                                        markerMaisProximo = locAtendimento
                                        distanciaMaisProxima = distancia
                                    }

                                    val marker = googleMap.addMarker(
                                        MarkerOptions().position(locAtendimento)
                                            .title(localizacao.nome)
                                    )
                                    if (marker != null) {
                                        markerList.add(marker)
                                    } else {
                                        markerList.clear()
                                    }
                                }
                            }

                            markerMaisProximo?.let {
                                val zoomLevel = 16f
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        it, zoomLevel
                                    )
                                )
                            }

                        }
                    }
                }
            }
        })
    }

    private fun calcularDistancia(location1: LatLng, location2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            location1.latitude,
            location1.longitude,
            location2.latitude,
            location2.longitude,
            results
        )
        return results[0]
    }

    private fun showErrorDialog(context: Context) {
        runOnUiThread {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Erro")
            builder.setMessage("Ocorreu um erro na busca. Se o problema persistir, verifique sua conexão com a internet ou entre em contato com o proprietário do aplicativo.")
            builder.setPositiveButton("OK", null)
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }
    }

}
