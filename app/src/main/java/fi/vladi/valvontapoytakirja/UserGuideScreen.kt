package fi.vladi.valvontapoytakirja

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.vladi.tarkastuspoytakirja.AppLanguage

@Composable
internal fun UserGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = AppLanguage.selected(context)
    val guide = UserGuideTexts.content[language] ?: UserGuideTexts.content["en"].orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back)) }
        Text(stringResource(R.string.user_guide_title), style = MaterialTheme.typography.headlineMedium)
        guide.lines().filter { it.isNotBlank() }.forEach { line ->
            val title = line.substringBefore("|")
            val body = line.substringAfter("|", "")
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back))
        }
    }
}

internal object UserGuideTexts {
    val content: Map<String, String> = mapOf(
        "ru" to """Назначение|Tarkastus Cloud предназначен для создания, хранения и обмена строительными отчётами: протоколом контроля гидроизоляции и протоколом осмотра. Документы PDF могут содержать сведения об объекте, материалах, исполнителях, результатах проверки и фотографии.
Первый запуск и языки|При первом запуске выберите язык. По умолчанию выбран английский. Поддерживаются английский, финский, шведский, норвежский, датский, эстонский, латышский, литовский и русский. Все кнопки, поля и сообщения переведены. PDF по умолчанию создаётся на языке приложения, но при составлении каждого отчёта язык можно изменить кнопкой вверху формы. Введённый вручную текст не переводится.
Вход|Можно продолжить без регистрации и работать локально, зарегистрироваться по электронной почте или войти через Google. После регистрации отправляется письмо для подтверждения адреса, но подтверждение не обязательно для входа. Для облачной синхронизации требуется вход в учётную запись.
Пароль и безопасность|Значок глаза показывает или скрывает пароль. Забытый пароль можно восстановить по электронной почте. На поддерживаемых устройствах доступна биометрическая защита. Приложение рассчитано на одного пользователя на персональном телефоне. Выход из аккаунта не удаляет локальные отчёты, фотографии и настройки.
Главный экран|Выберите «Протокол контроля гидроизоляции» или «Протокол осмотра». Значок шестерёнки открывает настройки и синхронизацию. Названия разделов отображаются на выбранном языке.
Первоначальные настройки|Укажите данные компании и инспектора, контакты, логотип, подпись, верхний и нижний колонтитулы, постоянных заказчиков и объекты. Логотип и подпись можно загрузить из файлов. Пустые необязательные поля не выводятся в PDF. Каждый сотрудник использует собственные персональные настройки.
Заказчики и объекты|Сохраните постоянных заказчиков и объекты для повторного использования. При создании отчёта выберите объект и при необходимости измените «Уточнение объекта», например номер квартиры.
Протокол контроля гидроизоляции|Выберите язык PDF вверху формы, заказчика и объект; укажите даты, исполнителя, материалы и результаты проверки. Добавьте замечания и фотографии, выберите «Принято» или «Отклонено», затем создайте PDF. Материалы Kiilto, Weber, Schönox, Ardex и Mapei подбираются с учётом совместимости. Гидроизоляция и выравнивающие смеси стен и пола указываются отдельно; для гидроизоляции предусмотрены расход и толщина сухого слоя. На странице PDF размещается до девяти фотографий.
Протокол осмотра|Выберите язык PDF, заказчика и объект. Для каждого пункта укажите помещение или расположение, описание ситуации и необходимые действия. Добавьте фотографии и следующие пункты осмотра. Лишние дополнительные пункты можно удалить. Фотографии размещаются вместе с замечаниями, до четырёх на странице; не поместившиеся изображения переносятся.
Фотографии|Используйте «Сделать фото» для камеры или «Выбрать фото» для галереи. Проверяйте соответствие изображений замечаниям и объекту. Ненужные фотографии можно удалить из отчёта. Выбранные из галереи снимки не дублируются в галерее.
PDF и хранение|Перед созданием PDF проверьте язык, данные и фотографии. Имя файла содержит тип отчёта и уточнение объекта, например valvontapoytakirja_B44.pdf. PDF сохраняются на устройстве, открываются стандартными программами и могут передаваться через Android. При синхронизации рабочие PDF доступны участникам общей папки.
Шаблоны|Шаблоны позволяют повторно использовать подготовленные данные отчёта. Перед созданием нового документа проверьте объект, даты, материалы, язык и результаты. Личные шаблоны не передаются автоматически через общую синхронизацию.
Общая папка Google Drive|Один сотрудник создаёт папку Google Drive и предоставляет коллегам права чтения и изменения. Каждый устанавливает приложение, входит в аккаунт и выбирает эту папку в настройках синхронизации. Отдельное приложение администратора не требуется. Доступ выдаётся и отзывается через Google Drive.
Синхронизация|Через общую папку передаются заказчики, объекты, рабочие отчёты, связанные фотографии и PDF. Персональные настройки, подписи и шаблоны отдельно не синхронизируются. Подключитесь к интернету, проверьте выбранную папку и запустите синхронизацию. Делайте это до и после совместной работы. Конфликтные версии сохраняются для снижения риска потери данных. Синхронизация не заменяет резервную копию.
Личные резервные копии|Выберите отдельную личную папку, не входящую в общую папку компании. Создайте ZIP-архив через раздел резервного копирования. Для восстановления выберите архив и подтвердите операцию. Восстановление может заменить текущие локальные данные; перед ним желательно создать свежую копию. Не храните личные архивы в общей папке.
Без интернета|Локальные отчёты можно создавать и редактировать без постоянного подключения. Для регистрации, входа, восстановления пароля и синхронизации нужен интернет. После восстановления соединения запустите синхронизацию.
Удаление отчётов|Используйте «Удалить» только после проверки необходимости документа. Удаление локального файла не обязательно удаляет облачную копию. Управляйте доступом сотрудников в Google Drive, а не удалением файлов.
Частые вопросы|Регистрация не нужна для локальной работы. Все функции доступны без подписки. Язык PDF можно выбрать отдельно от интерфейса. Ручной текст не переводится. PDF открываются без приложения. Подпись, включённая в готовый общий PDF, видна его получателям. Выход из аккаунта не удаляет локальные данные. При смене телефона используйте личную резервную копию и заново выберите общую папку. Если синхронизация не работает, проверьте интернет и права доступа. При увольнении сотрудника владелец папки отзывает доступ.
Ответственность|Приложение помогает документировать работы, но не заменяет профессиональную оценку инспектора, строительные нормы или инструкции производителей. За достоверность сведений и результаты проверки отвечает пользователь.
Рекомендации|Проверяйте язык и данные PDF; блокируйте телефон; не передавайте пароли; регулярно синхронизируйте данные и создавайте личные резервные копии; храните архивы отдельно от общей папки; перед удалением приложения убедитесь, что нужные данные сохранены.""",
        "en" to """Purpose|Tarkastus Cloud creates, stores and shares construction inspection reports: waterproofing inspection reports and general inspection reports. PDFs can include site details, materials, inspectors, findings and photos.
First launch and languages|Choose a language at first launch; English is the default. English, Finnish, Swedish, Norwegian, Danish, Estonian, Latvian, Lithuanian and Russian are supported. All interface labels and messages are translated. PDFs use the app language by default, but you can change the report language using the button at the top of each form. Text you type is not translated automatically.
Sign-in|Continue as a guest for local use, register with email, or sign in with Google. An email verification message is sent after registration, but verification is not required to sign in. Cloud sync requires a signed-in account.
Password and security|Use the eye icon to show or hide your password. Reset a forgotten password by email. Supported devices offer optional biometric protection. The app assumes one person per personal phone. Signing out does not delete local reports, photos or settings.
Home screen|Choose “Waterproofing inspection report” or “Inspection report”. The gear icon opens settings and synchronization. Labels follow the selected language.
Initial setup|Enter company and inspector details, contact information, logo, signature, header, footer, regular customers and sites. Upload the logo and signature from files. Empty optional fields are omitted from PDFs. Each employee maintains their own personal settings.
Customers and sites|Save regular customers and sites to reuse them. Select a site when creating a report and edit the site detail, such as an apartment number, when needed.
Waterproofing inspection report|Select the PDF language at the top, then customer, site, dates, worker, materials and findings. Add comments and photos, choose Approved or Rejected, and generate a PDF. Materials from Kiilto, Weber, Schönox, Ardex and Mapei must be compatible. Wall and floor waterproofing and leveling compounds are selected separately; waterproofing includes consumption and dry-film thickness. Up to nine photos appear per PDF page.
Inspection report|Choose PDF language, customer and site. For each inspection item, enter room or location, description and required actions. Add photos and further items; extra items can be removed. Photos stay with their findings, up to four per page, and move to the next page when necessary.
Photos|Use “Take photo” for the camera or “Choose photo” for the gallery. Check that photos match the site and findings. Remove unwanted photos from the report. Gallery photos are not duplicated in the gallery.
PDF and storage|Check language, details and photos before generating a PDF. The filename includes the report type and site detail, for example valvontapoytakirja_B44.pdf. PDFs are stored on the device, open in standard viewers and can be shared through Android. Synchronized work PDFs are accessible to members of the shared folder.
Templates|Templates reuse prepared report details. Check site, dates, materials, language and results before creating a new report. Personal templates are not shared automatically.
Shared Google Drive folder|One employee creates a Google Drive folder and grants colleagues read and edit access. Each person installs the app, signs in and selects the folder in sync settings. No separate administrator app is needed. Manage access in Google Drive.
Synchronization|The shared folder synchronizes customers, sites, work reports, related photos and PDFs. Personal settings, signatures and templates are not synchronized separately. Connect to the internet, verify the folder and start sync. Sync before and after shared work. Conflicting versions are retained to reduce data loss. Sync is not a backup.
Private backups|Choose a separate private folder outside the company shared folder. Create a ZIP backup in the backup section. To restore, select an archive and confirm. Restoring may replace current local data; create a fresh backup first. Never store private backups in the shared folder.
Offline use|Create and edit local reports without a constant internet connection. Registration, sign-in, password reset and sync need internet. Synchronize when connectivity returns.
Deleting reports|Use Delete only after checking that the document is no longer needed. Deleting a local file does not necessarily delete its cloud copy. Control employee access through Google Drive.
Frequently asked questions|Registration is optional for local work. All features are available without a subscription. PDF language can differ from the interface. Typed text is not translated. PDFs open without the app. A signature included in a shared PDF is visible to its recipients. Signing out keeps local data. When changing phones, restore a private backup and select the shared folder again. For sync problems check internet and permissions. Revoke a departing employee’s Drive access.
Responsibility|The app documents work but does not replace professional judgment, building regulations or manufacturers’ instructions. The user is responsible for the accuracy of entered information and findings.
Recommendations|Check PDF language and details; lock your phone; never share passwords; synchronize regularly and make private backups; keep backups outside the shared folder; save necessary data before uninstalling."""
    )
}
