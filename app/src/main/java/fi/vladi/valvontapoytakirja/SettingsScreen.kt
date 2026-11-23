package fi.vladi.valvontapoytakirja

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import fi.vladi.valvontapoytakirja.model.InspectorProfile
import fi.vladi.valvontapoytakirja.model.AppSettings
import fi.vladi.valvontapoytakirja.model.CustomerProfile
import fi.vladi.valvontapoytakirja.model.CustomerSite
import fi.vladi.valvontapoytakirja.model.SiteFieldDefinition
import java.util.UUID

@Composable
fun SettingsScreen(
    settings: AppSettings,
    customers: List<CustomerProfile>,
    inspectors: List<InspectorProfile>,
    onSaveInspector: (InspectorProfile) -> Unit,
    onDeleteInspector: (String) -> Unit,
    sites: List<CustomerSite>,
    onSettingsChange: (AppSettings) -> Unit,
    onSaveCustomer: (CustomerProfile) -> Unit,
    onDeleteCustomer: (String) -> Unit,
    onSaveSite: (CustomerSite) -> Unit,
    onDeleteSite: (String) -> Unit,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onPickSignature: () -> Unit,
    onRemoveSignature: () -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var editingInspector by remember { mutableStateOf<InspectorProfile?>(null) }
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
            TextButton(onClick = onBack) { Text(stringResource(R.string.settings_back)) }
            Text(stringResource(R.string.settings_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        SettingsSection(stringResource(R.string.settings_personal))
        SettingField(stringResource(R.string.settings_inspector), settings.inspectorName) {
            onSettingsChange(settings.copy(inspectorName = it))
        }
        SettingField(stringResource(R.string.settings_cert), settings.inspectorCertificateNumber) {
            onSettingsChange(settings.copy(inspectorCertificateNumber = it))
        }
        SettingField(stringResource(R.string.settings_phone), settings.inspectorPhone) {
            onSettingsChange(settings.copy(inspectorPhone = it))
        }
        SettingField(stringResource(R.string.settings_email), settings.inspectorEmail) {
            onSettingsChange(settings.copy(inspectorEmail = it))
        }
        Text(stringResource(R.string.settings_logo))
        val logoBitmap by produceState<Bitmap?>(initialValue = null, key1 = settings.logoPath) {
            value = if (settings.logoPath.isBlank()) null else withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(settings.logoPath) }.getOrNull()
            }
        }
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap!!.asImageBitmap(),
                contentDescription = stringResource(R.string.settings_logo),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(if (settings.logoPath.isBlank()) stringResource(R.string.settings_no_logo) else stringResource(R.string.settings_logo_missing))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickLogo, modifier = Modifier.weight(1f)) {
                Text(if (settings.logoPath.isBlank()) stringResource(R.string.settings_choose_logo) else stringResource(R.string.settings_change_logo))
            }
            if (settings.logoPath.isNotBlank()) {
                TextButton(onClick = onRemoveLogo) { Text(stringResource(R.string.settings_delete)) }
            }
        }
        SettingField(stringResource(R.string.settings_header), settings.headerText, minLines = 2) {
            onSettingsChange(settings.copy(headerText = it))
        }
        SettingField(stringResource(R.string.settings_footer), settings.footerText, minLines = 2) {
            onSettingsChange(settings.copy(footerText = it))
        }
        Text(stringResource(R.string.settings_signature), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        val signatureBitmap by produceState<Bitmap?>(initialValue = null, key1 = settings.signatureImagePath) {
            value = if (settings.signatureImagePath.isBlank()) null else withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(settings.signatureImagePath) }.getOrNull()
            }
        }
        if (settings.signatureImagePath.isNotBlank()) {
            if (signatureBitmap != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Image(
                        bitmap = signatureBitmap!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.settings_signature),
                        modifier = Modifier.fillMaxWidth().height(190.dp).padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            } else {
                Text(stringResource(R.string.settings_signature_missing))
            }
        } else {
            Text(stringResource(R.string.settings_signature_hint))
        }
        SignaturePad(
            strokes = settings.signatureStrokes,
            onChange = { onSettingsChange(settings.copy(signatureStrokes = it, signatureImagePath = "")) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickSignature, modifier = Modifier.weight(1f)) {
                Text(if (settings.signatureImagePath.isBlank()) stringResource(R.string.settings_upload_signature) else stringResource(R.string.settings_change_signature))
            }
            if (settings.signatureImagePath.isNotBlank()) TextButton(onClick = onRemoveSignature) { Text(stringResource(R.string.settings_remove_image)) }
        }

        SettingsSection(stringResource(R.string.inspector_profiles))
        inspectors.forEach { inspector ->
            Card(modifier = Modifier.fillMaxWidth().clickable { editingInspector = inspector }) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(inspector.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editingInspector = inspector }) { Text(stringResource(R.string.settings_edit_customers)) }
                }
            }
        }
        OutlinedButton(onClick = { editingInspector = InspectorProfile() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.inspector_add))
        }
        if (settings.inspectorName.isNotBlank() &&
            inspectors.none { it.name.trim().equals(settings.inspectorName.trim(), ignoreCase = true) }
        ) {
            OutlinedButton(
                onClick = {
                    editingInspector = InspectorProfile(
                        name = settings.inspectorName,
                        certificateNumber = settings.inspectorCertificateNumber,
                        phone = settings.inspectorPhone,
                        email = settings.inspectorEmail,
                        signatureStrokes = settings.signatureStrokes,
                        signatureImagePath = ""
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.inspector_add_current)) }
        }
        editingInspector?.let { draft ->
            SettingField(stringResource(R.string.settings_inspector), draft.name) { editingInspector = draft.copy(name = it) }
            SettingField(stringResource(R.string.settings_cert), draft.certificateNumber) { editingInspector = draft.copy(certificateNumber = it) }
            SettingField(stringResource(R.string.settings_phone), draft.phone) { editingInspector = draft.copy(phone = it) }
            SettingField(stringResource(R.string.settings_email), draft.email) { editingInspector = draft.copy(email = it) }
            Text(stringResource(R.string.settings_signature), style = MaterialTheme.typography.titleMedium)
            SignaturePad(
                strokes = draft.signatureStrokes,
                onChange = { editingInspector = draft.copy(signatureStrokes = it, signatureImagePath = "") }
            )
            if (draft.signatureStrokes.isEmpty() && settings.signatureStrokes.isNotEmpty()) {
                OutlinedButton(onClick = {
                    editingInspector = draft.copy(signatureStrokes = settings.signatureStrokes, signatureImagePath = "")
                }) { Text(stringResource(R.string.inspector_copy_signature)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSaveInspector(draft); editingInspector = null }, enabled = draft.name.isNotBlank()) {
                    Text(stringResource(R.string.settings_save_customer))
                }
                TextButton(onClick = {
                    if (inspectors.any { it.id == draft.id }) onDeleteInspector(draft.id)
                    editingInspector = null
                }) { Text(stringResource(R.string.settings_delete_action)) }
            }
        }

        SettingsSection(stringResource(R.string.settings_customers))
        Text(stringResource(R.string.settings_customers_hint))
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
                    Text(stringResource(R.string.settings_sites_count, sites.count { it.customerId == customer.id }), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        selectedCustomerId = customer.id
                        editingCustomer = customer
                        editingSite = null
                    }) { Text(stringResource(R.string.settings_edit_customers)) }
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
        ) { Text(stringResource(R.string.settings_add_customer)) }

        if (editingCustomer != null) {
            val draft = editingCustomer!!
            SettingsSection(if (draft.name.isBlank()) stringResource(R.string.customer_new_title) else stringResource(R.string.customer_details_title))
            SettingField(stringResource(R.string.settings_company), draft.name) { editingCustomer = draft.copy(name = it) }
            SettingField(stringResource(R.string.settings_business_id), draft.businessId) { editingCustomer = draft.copy(businessId = it) }
            SettingField(stringResource(R.string.settings_billing), draft.address, minLines = 2) { editingCustomer = draft.copy(address = it) }
            SettingField(stringResource(R.string.settings_postal_code), draft.postalCode) { editingCustomer = draft.copy(postalCode = it) }
            SettingField(stringResource(R.string.settings_city), draft.city) { editingCustomer = draft.copy(city = it) }
            SettingField(stringResource(R.string.settings_contact), draft.contactPerson) { editingCustomer = draft.copy(contactPerson = it) }
            SettingField(stringResource(R.string.settings_phone), draft.phone) { editingCustomer = draft.copy(phone = it) }
            SettingField(stringResource(R.string.settings_email), draft.email) { editingCustomer = draft.copy(email = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveCustomer(draft)
                        selectedCustomerId = draft.id
                        editingCustomer = draft
                    },
                    enabled = draft.name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_save_customer)) }
                TextButton(onClick = {
                    if (customers.any { it.id == draft.id }) onDeleteCustomer(draft.id)
                    editingCustomer = null
                    selectedCustomerId = ""
                    editingSite = null
                }) { Text(if (customers.any { it.id == draft.id }) stringResource(R.string.settings_delete_action) else stringResource(R.string.settings_cancel_action)) }
            }
        }

        if (selectedCustomer != null) {
            SettingsSection(stringResource(R.string.customer_sites_title, selectedCustomer.name))
            customerSites.forEach { site ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(site.name, fontWeight = FontWeight.SemiBold)
                        Text(site.address)
                        if (site.fieldDefinitions.isNotEmpty()) {
                            Text(stringResource(R.string.settings_extra_fields, site.fieldDefinitions.joinToString { it.label }), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { editingSite = site }) { Text(stringResource(R.string.settings_edit_site)) }
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    editingSite = CustomerSite(customerId = selectedCustomer.id)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_add_site)) }
        }

        if (editingSite != null) {
            val draft = editingSite!!
            SettingsSection(if (draft.name.isBlank()) stringResource(R.string.site_new_title) else stringResource(R.string.site_details_title))
            SettingField(stringResource(R.string.settings_site_name), draft.name) { editingSite = draft.copy(name = it) }
            SettingField(stringResource(R.string.settings_site_detail), draft.address, minLines = 2) { editingSite = draft.copy(address = it) }
            Text(stringResource(R.string.settings_site_fields), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_site_fields_hint))
            draft.fieldDefinitions.forEach { definition ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingField(stringResource(R.string.settings_field_name), definition.label, modifier = Modifier.weight(1f)) { value ->
                        editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions.map {
                            if (it.id == definition.id) it.copy(label = value) else it
                        })
                    }
                    TextButton(onClick = {
                        editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions.filterNot { it.id == definition.id })
                    }) { Text(stringResource(R.string.settings_delete)) }
                }
            }
            OutlinedButton(
                onClick = {
                    editingSite = draft.copy(fieldDefinitions = draft.fieldDefinitions + SiteFieldDefinition(label = context.getString(R.string.site_new_field)))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_add_field)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveSite(draft)
                        editingSite = null
                    },
                    enabled = draft.name.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.settings_save_site)) }
                TextButton(onClick = {
                    if (sites.any { it.id == draft.id }) onDeleteSite(draft.id)
                    editingSite = null
                }) { Text(if (sites.any { it.id == draft.id }) stringResource(R.string.settings_delete_action) else stringResource(R.string.settings_cancel_action)) }
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
