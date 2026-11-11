package fi.vladi.tarkastuspoytakirja

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.model.AppSettings
import fi.vladi.tarkastuspoytakirja.model.CustomerProfile
import fi.vladi.tarkastuspoytakirja.model.CustomerSite
import fi.vladi.tarkastuspoytakirja.model.SiteFieldDefinition
import java.util.UUID

@Composable
fun SettingsScreen(
    settings: AppSettings,
    customers: List<CustomerProfile>,
    sites: List<CustomerSite>,
    onSettingsChange: (AppSettings) -> Unit,
    onSaveCustomer: (CustomerProfile) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onSaveSite: (CustomerSite) -> Unit,
    onDeleteSite: (String) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onBack: () -> Unit
) {
    var editingCustomer by remember { mutableStateOf<CustomerProfile?>(null) }
    var editingSite by remember { mutableStateOf<CustomerSite?>(null) }
    var selectedCustomerId by remember { mutableStateOf("") }
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    val customerSites = sites.filter { it.customerId == selectedCustomerId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("← Takaisin") }
            Text("ASETUKSET", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        SettingsSection("OMAT TIEDOT JA RAPORTIN ULKOASU")
        SettingField("Tarkastajan nimi", settings.inspectorName) {
            onSettingsChange(settings.copy(inspectorName = it))
        }
        SettingField("Sertifikaattinumero", settings.inspectorCertificateNumber) {
            onSettingsChange(settings.copy(inspectorCertificateNumber = it))
        }
        SettingField("Puhelin", settings.inspectorPhone) {
            onSettingsChange(settings.copy(inspectorPhone = it))
        }
        SettingField("Sähköposti", settings.inspectorEmail) {
            onSettingsChange(settings.copy(inspectorEmail = it))
        }
        Text("Logo")
        Text(if (settings.logoPath.isBlank()) "Logoa ei ole valittu." else "Logo tallennettu.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickLogo, modifier = Modifier.weight(1f)) {
                Text(if (settings.logoPath.isBlank()) "Valitse logo" else "Vaihda logo")
            }
            if (settings.logoPath.isNotBlank()) {
                TextButton(onClick = onRemoveLogo) { Text("Poista") }
            }
        }
        SettingField("Yläkolontti / otsikkoteksti", settings.headerText, minLines = 2) {
            onSettingsChange(settings.copy(headerText = it))
        }
        SettingField("Alatunniste / yhteystiedot", settings.footerText, minLines = 2) {
            onSettingsChange(settings.copy(footerText = it))
        }
        Text("Allekirjoitus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SignaturePad(
            strokes = settings.signatureStrokes,
            onChange = { onSettingsChange(settings.copy(signatureStrokes = it)) }
        )

        SettingsSection("VAKIOASIAKKAAT")
        Text("Tallenna asiakkaat kerran. Uutta pöytäkirjaa tehdessä valitset asiakkaan ja kohteen listasta.")
        customers.forEach { customer ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    selectedCustomerId = customer.id
                    editingCustomer = customer
                    editingSite = null
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(customer.name, fontWeight = FontWeight.SemiBold)
                    Text(listOf(customer.businessId, customer.city).filter { it.isNotBlank() }.joinToString(" • "))
                    Text("Kohteita: ${sites.count { it.customerId == customer.id }}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selectedCustomerId = customer.id
                        editingCustomer = customer
                        editingSite = null
                    }) { Text("Muokkaa asiakasta ja kohteita") }
                }
            }
        }
        OutlinedButton(
            onClick = {
                editingCustomer = CustomerProfile()
                selectedCustomerId = ""
                editingSite = null
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Lisää vakioasiakas") }

        if (editingCustomer != null) {
            val draft = editingCustomer!!
            SettingsSection(if (draft.name.isBlank()) "UUSI ASIAKAS" else "ASIAKKAAN TIEDOT")
            SettingField("Yrityksen / tilaajan nimi", draft.name) { editingCustomer = draft.copy(name = it) }
            SettingField("Y-tunnus", draft.businessId) { editingCustomer = draft.copy(businessId = it) }
            SettingField("Laskutusosoite", draft.address, minLines = 2) { editingCustomer = draft.copy(address = it) }
            SettingField("Postinumero", draft.postalCode) { editingCustomer = draft.copy(postalCode = it) }
            SettingField("Postitoimipaikka", draft.city) { editingCustomer = draft.copy(city = it) }
            SettingField("Yhteyshenkilö", draft.contactPerson) { editingCustomer = draft.copy(contactPerson = it) }
            SettingField("Puhelin", draft.phone) { editingCustomer = draft.copy(phone = it) }
            SettingField("Sähköposti", draft.email) { editingCustomer = draft.copy(email = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveCustomer(draft)
                        selectedCustomerId = draft.id
                        editingCustomer = draft
                    },
                    enabled = draft.name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Tallenna asiakas") }
                TextButton(onClick = {
                    if (customers.any { it.id == draft.id }) onDeleteCustomer(draft.id)
                    editingCustomer = null
                    selectedCustomerId = ""
                    editingSite = null
                }) { Text(if (customers.any { it.id == draft.id }) "Poista" else "Peruuta") }
            }
        }

        if (selectedCustomer != null) {
            SettingsSection("KOHTEET: ${selectedCustomer.name}")
            customerSites.forEach { site ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(site.name, fontWeight = FontWeight.SemiBold)
                        Text(site.address)
                        if (site.fieldDefinitions.isNotEmpty()) {
                            Text("Lisäkentät: " + site.fieldDefinitions.joinToString { it.label }, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { editingSite = site }) { Text("Muokkaa kohdetta") }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    editingSite = CustomerSite(customerId = selectedCustomer.id)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Lisää kohde") }
        }

        if (editingSite != null) {
            val draft = editingSite!!
            SettingsSection(if (draft.name.isBlank()) "UUSI KOHDE" else "KOHTEEN TIEDOT")
            SettingField("Kohteen nimi / tunniste", draft.name) { editingSite = draft.copy(name = it) }
            SettingField("Kohteen osoite", draft.address, minLines = 2) { editingSite = draft.copy(address = it) }
            Text("Kohteen omat lisäkentät", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Esimerkiksi huoneiston numero, rappu, kerros tai sopimusnumero. Nämä kentät tulevat näkyviin raporttiin.")
            draft.fieldDefinitions.forEach { definition ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingField("Kentän nimi", definition.label, modifier = Modifier.weight(1f)) { value ->
                        editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions.map {
                            if (it.id == definition.id) it.copy(label = value) else it
                        })
                    }
                    TextButton(onClick = {
                        editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions.filterNot { it.id == definition.id })
                    }) { Text("Poista") }
                }
            }
            OutlinedButton(
                onClick = {
                    editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions + SiteFieldDefinition(label = "Uusi kenttä"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Lisää kenttä") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveSite(draft)
                        editingSite = null
                    },
                    enabled = draft.name.isNotBlank() && draft.address.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Tallenna kohde") }
                TextButton(onClick = {
                    if (sites.any { it.id == draft.id }) onDeleteSite(draft.id)
                    editingSite = null
                }) { Text(if (sites.any { it.id == draft.id }) "Poista" else "Peruuta") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(title: String) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = minLines == 1
    )
}
