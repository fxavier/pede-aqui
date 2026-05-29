const fs = require('fs');
const path = require('path');

const SCREENS_DIR = path.resolve(__dirname, '../public/imported-screens');

const monthNames = new Set([
  'january', 'february', 'march', 'april', 'may', 'june',
  'july', 'august', 'september', 'october', 'november', 'december',
  'jan', 'feb', 'mar', 'apr', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec',
]);

const knownNonTranslatable = new Set([
  'swiftcourier', 'pede aqui', 'pede-aqui', 'pede', 'aqui',
  'flex', 'grid', 'sticky', 'relative', 'absolute', 'fixed',
  'inline-block', 'overflow', 'auto', 'scroll', 'clip',
  'material-symbols-outlined',
  'fill', 'weight', 'currentcolor', 'currentColor',
  'data-icon', 'data-weight', 'data-alt',
  'topappbar', 'top-app-bar', 'topnavbar', 'sidenavbar', 'sidenav',
  'side navbar', 'sidebar', 'navbar', 'nav', 'header',
  'footer', 'main', 'aside', 'section', 'article',
  'live', 'out', 'pending', 'new', 'preparing', 'ready',
  'admin', 'vendor',
  'chevron_left', 'chevron_right', 'expand_more', 'expand_less',
  'arrow_back', 'arrow_forward', 'close', 'menu', 'more_vert', 'more_horiz',
  'search', 'notifications', 'home', 'dashboard', 'settings', 'help_outline',
  'logout', 'login', 'signup', 'register',
  'add_circle', 'add', 'edit', 'delete', 'save', 'upload', 'download',
  'refresh', 'done', 'clear', 'cancel', 'check_circle',
  'restaurant_menu', 'restaurant', 'menu_book', 'local_dining',
  'store', 'storefront', 'shopping_cart', 'shopping_bag',
  'inventory', 'inventory_2', 'category', 'label', 'sell',
  'local_offer', 'local_shipping', 'local_activity',
  'payments', 'account_balance', 'account_balance_wallet',
  'receipt_long', 'receipt', 'request_quote',
  'assessment', 'bar_chart', 'show_chart', 'analytics', 'insights',
  'monitoring', 'monitor_heart', 'trending_up', 'trending_down',
  'leaderboard', 'workspace_premium',
  'group', 'groups', 'person', 'people', 'person_add', 'person_search',
  'badge', 'verified', 'verified_user', 'id_card',
  'security', 'shield', 'lock', 'lock_open', 'gavel',
  'history', 'history_edu', 'history_toggle_off',
  'schedule', 'calendar_today', 'calendar_month', 'date_range',
  'timer', 'timer_off', 'access_time', 'hourglass_empty',
  'settings', 'settings_applications', 'settings_suggest',
  'tune', 'style', 'tips_and_updates', 'lightbulb',
  'help_center', 'support', 'support_agent', 'headset_mic',
  'email', 'mail', 'phone', 'call', 'chat', 'sms', 'send',
  'notifications_active', 'notifications_none',
  'location_on', 'map', 'near_me', 'pin_drop', 'explore',
  'package', 'package_2', 'inventory', 'category',
  'assignment', 'assignment_return', 'assignment_turned_in',
  'description', 'article', 'note_add', 'post_add', 'edit_note',
  'file_download', 'file_upload', 'file_present',
  'content_copy', 'content_paste', 'content_cut',
  'visibility', 'visibility_off', 'remove_red_eye',
  'star', 'star_half', 'star_border', 'favorite', 'favorite_border',
  'thumb_up', 'thumb_down', 'reply', 'report', 'flag',
  'play_arrow', 'pause', 'stop', 'play_circle', 'pause_circle',
  'info', 'warning', 'error', 'error_outline', 'new_releases',
  'check', 'checklist', 'task_alt', 'task',
  'redeem', 'card_giftcard', 'confirmation_number',
  'qr_code', 'qr_code_scanner', 'credit_card',
  'money', 'attach_money', 'paid', 'price_change', 'price_check',
  'percent', 'tag', 'point_of_sale',
  'rocket_launch', 'rocket', 'flight_takeoff', 'flight_land',
  'electric_moped', 'pedal_bike', 'motorcycle', 'directions_car',
  'local_shipping', 'airport_shuttle', 'two_wheeler',
  'hub', 'lan', 'cloud_sync', 'cloud_upload', 'cloud_download',
  'public', 'language', 'travel_explore',
  'wifi', 'signal_cellular_alt', 'memory', 'dns',
  'light_mode', 'dark_mode', 'brightness_6',
  'camera_alt', 'photo_camera', 'image', 'imagesmode',
  'checklist_rtl', 'checklist',
  'drag_indicator', 'drag_handle', 'reorder',
  'menu_open', 'double_arrow', 'subdirectory_arrow_left',
  'open_in_new', 'open_in_full', 'close_fullscreen',
  'fullscreen', 'fullscreen_exit',
  'zoom_in', 'zoom_out', 'search', 'search_off',
  'filter_list', 'filter_alt', 'sort', 'sort_by_alpha',
  'view_list', 'view_module', 'view_column', 'view_agenda',
  'grid_on', 'grid_view', 'table_chart', 'table_rows',
  'list', 'list_alt', 'format_list_bulleted',
  'refresh', 'sync', 'sync_problem', 'sync_disabled',
  'update', 'upgrade', 'download', 'upload',
  'undo', 'redo', 'rotate_left', 'rotate_right',
  'cached', 'autorenew', 'loop',
  'eco', 'energy_savings_leaf', 'nature',
  'celebration', 'confetti', 'celebration_outline',
  'campaign', 'speaker_notes', 'volume_up',
  'handshake', 'diversity_3', 'emoji_people',
  'terminal', 'code', 'javascript', 'typescript',
  'transaction_log', 'real-time', 'realtime',
  'grid-cols-1', 'grid-cols-2', 'grid-cols-3', 'grid-cols-4', 'grid-cols-5', 'grid-cols-6',
  'col-span-1', 'col-span-2', 'col-span-3', 'col-span-4', 'col-span-5', 'col-span-6',
  'desktop', 'mobile', 'tablet',
  'edit_calendar',
  'ios_share',
  'event_busy',
  'energy_savings_leaf',
  'attach_file',
  'sell',
  'rule_settings',
  'text_rotate_up',
  'check_indeterminate_small',
  'bookmark',
  'how_to_vote',
  'newsmode',
  'emergency',
  'apparel',
  'nutrition',
  'humidity_percentage',
  'apartment', 'apps', 'archive', 'block', 'bolt', 'database', 'diamond',
  'cloud_sync', 'lunch_dining', 'report_problem', 'security_update_good',
  'admin_panel_settings', 'settings_applications', 'settings_suggest',
  'verified_user', 'workspace_premium', 'assignment_late', 'new_releases',
  'car_rental', 'delete_outline', 'edit_calendar', 'edit_note',
  'arrow_downward', 'arrow_upward', 'timer_10_alt_1', 'energy_savings_leaf',
  'draw', 'edit', 'ios_share', 'event_busy', 'add_box', 'add_a_photo',
  'add_shopping_cart', 'auto_awesome', 'sell', 'rule_settings',
  'text_rotate_up', 'check_indeterminate_small', 'how_to_vote', 'newsmode',
  'emergency', 'apparel', 'nutrition', 'humidity_percentage',
  'add_circle', 'check_circle', 'cancel', 'menu', 'more_vert',
  'more_horiz', 'arrow_back', 'arrow_forward', 'close', 'done',
  'clear', 'refresh', 'search', 'notifications', 'home', 'settings',
  'help_outline', 'info', 'warning', 'error', 'error_outline',
  'star', 'star_half', 'star_border', 'favorite', 'favorite_border',
  'share', 'print', 'lock', 'lock_open', 'visibility', 'visibility_off',
  'menu_book', 'local_dining', 'local_shipping', 'local_offer',
  'local_activity', 'receipt_long', 'receipt', 'request_quote',
  'account_balance', 'account_balance_wallet', 'assessment',
  'bar_chart', 'show_chart', 'analytics', 'insights', 'monitoring',
  'monitor_heart', 'trending_up', 'trending_down', 'leaderboard',
  'person', 'people', 'person_add', 'person_search', 'badge',
  'id_card', 'security', 'shield', 'gavel', 'history', 'history_edu',
  'schedule', 'calendar_today', 'calendar_month', 'date_range',
  'timer', 'timer_off', 'access_time', 'hourglass_empty', 'tune',
  'style', 'tips_and_updates', 'lightbulb', 'help_center',
  'support_agent', 'headset_mic', 'email', 'mail', 'phone', 'call',
  'chat', 'sms', 'send', 'notifications_active', 'notifications_none',
  'location_on', 'map', 'near_me', 'pin_drop', 'explore', 'package',
  'package_2', 'assignment', 'assignment_return', 'assignment_turned_in',
  'description', 'article', 'note_add', 'post_add', 'edit_note',
  'file_download', 'file_upload', 'content_copy', 'content_paste',
  'remove_red_eye', 'thumb_up', 'thumb_down', 'reply', 'report',
  'flag', 'play_arrow', 'pause', 'stop', 'play_circle', 'pause_circle',
  'new_releases', 'checklist', 'task_alt', 'task', 'redeem',
  'card_giftcard', 'confirmation_number', 'qr_code', 'qr_code_scanner',
  'credit_card', 'attach_money', 'paid', 'price_change', 'price_check',
  'percent', 'tag', 'point_of_sale', 'rocket_launch',
  'flight_takeoff', 'flight_land', 'electric_moped', 'pedal_bike',
  'motorcycle', 'directions_car', 'airport_shuttle', 'two_wheeler',
  'hub', 'lan', 'cloud_upload', 'cloud_download', 'public',
  'language', 'travel_explore', 'wifi', 'signal_cellular_alt',
  'memory', 'dns', 'light_mode', 'dark_mode', 'camera_alt',
  'image', 'drag_indicator', 'drag_handle', 'reorder', 'menu_open',
  'open_in_new', 'open_in_full', 'fullscreen', 'zoom_in', 'zoom_out',
  'filter_list', 'filter_alt', 'sort', 'view_list', 'view_module',
  'grid_on', 'grid_view', 'table_chart', 'list_alt',
  'sync', 'sync_problem', 'undo', 'redo', 'rotate_left',
  'rotate_right', 'cached', 'autorenew', 'loop', 'eco',
  'celebration', 'campaign', 'speaker_notes', 'volume_up', 'handshake',
  'diversity_3', 'emoji_people', 'terminal', 'code', 'check_indeterminate_small',
  'bookmark', 'how_to_vote', 'newsmode', 'apparel', 'cloud_sync',
  'lunch_dining', 'report_problem', 'security_update_good',
  'admin_panel_settings', 'settings_applications', 'settings_suggest',
  'verified_user', 'workspace_premium', 'assignment_late',
  'car_rental', 'delete_outline', 'edit_calendar', 'edit_note',
  'arrow_downward', 'arrow_upward', 'timer_10_alt_1',
  'add_box', 'add_a_photo', 'add_shopping_cart', 'auto_awesome',
  'sell', 'rule_settings', 'text_rotate_up', 'ios_share', 'event_busy',
  'energy_savings_leaf', 'attach_file', 'draw', 'edit', 'wash',
  'humidity_percentage', 'nutrition', 'emergency', 'order_approve',
  'conversion_path', 'text_snippet', 'no_meals', 'rainy',
  'sunny', 'cloudy', 'partly_cloudy', 'ac_unit', 'whatshot',
  'format_list_bulleted', 'attach_money', 'receipt_long', 'receipt',
  'point_of_sale', 'confirmation_number', 'price_check', 'price_change',
  'check_indeterminate_small', 'storefront', 'shopping_cart',
  'shopping_bag', 'inventory', 'inventory_2', 'category', 'label',
  'date_range', 'calendar_month', 'calendar_today', 'schedule',
  'access_time', 'hourglass_empty', 'notifications_active',
  'notifications_none', 'notifications', 'support_agent',
  'headset_mic', 'account_balance_wallet', 'account_balance',
]);

// Portuguese words - if a string is entirely or mostly Portuguese, skip it
const ptWords = new Set([
  'a', 'ao', 'aos', 'as', 'às', 'com', 'como', 'da', 'das', 'de', 'do',
  'dos', 'e', 'em', 'entre', 'essa', 'essas', 'esse', 'esses', 'esta',
  'estas', 'este', 'estes', 'foi', 'foram', 'há', 'isso', 'isto',
  'mas', 'mesmo', 'na', 'nas', 'no', 'nos', 'num', 'nuns', 'numa',
  'numas', 'o', 'os', 'ou', 'para', 'peça', 'pela', 'pelas', 'pelo',
  'pelos', 'perante', 'pode', 'podem', 'pois', 'por', 'porém',
  'qual', 'quando', 'que', 'quem', 'se', 'sem', 'sendo', 'ser',
  'seu', 'seus', 'sua', 'suas', 'tal', 'também', 'teu', 'teus',
  'ti', 'tua', 'tuas', 'tem', 'têm', 'temos', 'tive', 'todos',
  'tu', 'um', 'uma', 'umas', 'uns', 'vale', 'vem', 'vêm',
  'zero', 'um', 'dois', 'três', 'quatro', 'cinco', 'seis', 'sete',
  'oito', 'nove', 'dez', 'onze', 'doze', 'treze', 'catorze',
  'quinze', 'dezasseis', 'dezassete', 'dezoito', 'dezanove', 'vinte',
  'trinta', 'quarenta', 'cinquenta', 'sessenta', 'setenta',
  'oitenta', 'noventa', 'cem', 'cento', 'duzentos', 'trezentos',
  'quatrocentos', 'quinhentos', 'seiscentos', 'setecentos',
  'oitocentos', 'novecentos', 'mil', 'milhão', 'milhões',
  'pedidos', 'pedido', 'cardápio', 'cardapio',
  'todos', 'todas', 'todo', 'toda',
  'mais', 'menos', 'muito', 'pouco', 'pouca', 'poucos', 'poucas',
  'entrega', 'entregas', 'entregador', 'entregadores',
  'restaurante', 'restaurantes',
  'cliente', 'clientes',
  'pagamento', 'pagamentos',
  'pede', 'aqui',
  'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado', 'domingo',
  'segunda-feira', 'terça-feira', 'quarta-feira', 'quinta-feira',
  'sexta-feira',
  'gratuito', 'gratuita', 'grátis',
  'janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho',
  'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro',
  'loja', 'lojas',
  'categoria', 'categorias',
  'produto', 'produtos', 'preço', 'preços',
  'status', 'ativo', 'inativo', 'ativo', 'inativo',
  'editar', 'excluir', 'criar', 'salvar', 'cancelar',
  'usuário', 'usuários', 'administrador', 'administradores',
]);

function getAllHTMLFiles(dir) {
  return fs.readdirSync(dir).filter(f => f.endsWith('.html'));
}

function cleanText(text) {
  return text
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractTextNodes(html) {
  const results = [];

  // Remove script, style, comment, SVG blocks, and Material Icons text nodes (ligatures)
  let cleaned = html
    .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ')
    .replace(/<svg[^>]*>[\s\S]*?<\/svg>/gi, ' ');
  // Strip Material Symbols icon ligatures (text inside spans with material-symbols class)
  cleaned = cleaned.replace(/<span[^>]*material-symbols[^>]*>[\s\S]*?<\/span>/gi, ' ');

  // Extract individual text nodes: >text< (non-greedy between tags)
  const textNodeRegex = />([^<]+)</g;
  let match;
  while ((match = textNodeRegex.exec(cleaned)) !== null) {
    const text = match[1].trim();
    if (text) results.push(text);
  }

  // Extract alt text (but not generated AI descriptions)
  const altRegex = /alt="([^"]+?)"/g;
  while ((match = altRegex.exec(cleaned)) !== null) {
    const alt = match[1].trim();
    if (alt && alt.length < 100) results.push(alt);
  }

  // Extract placeholder text
  const phRegex = /placeholder="([^"]+?)"/g;
  while ((match = phRegex.exec(cleaned)) !== null) {
    const ph = match[1].trim();
    if (ph) results.push(ph);
  }

  // Extract title attribute text
  const titleRegex = /title="([^"]+?)"/g;
  while ((match = titleRegex.exec(cleaned)) !== null) {
    const t = match[1].trim();
    if (t && t.length < 200) results.push(t);
  }

  return results;
}

function isEnglishText(s) {
  // Must contain at least some ASCII letters
  if (!/[A-Za-z]{2,}/.test(s)) return false;
  // Must not contain non-Latin scripts
  if (/[\u0600-\u06FF\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF]/.test(s)) return false;
  return true;
}

function isMostlyEnglish(s) {
  const letters = s.replace(/[^A-Za-zÀ-ÿ]/g, '');
  if (!letters) return false;
  const asciiLetters = s.replace(/[^A-Za-z]/g, '');
  return asciiLetters.length / letters.length >= 0.85;
}

function countPortugueseWords(words) {
  return words.filter(w => ptWords.has(w)).length;
}

function shouldFilter(s) {
  const original = s;
  const lower = s.toLowerCase().trim();

  if (!lower) return true;
  if (lower.length < 2) return true;

  // Pure numbers or numeric with currency/measurement
  if (/^[\d,.\s%$€£₦R$$\-+]+$/.test(lower)) return true;

  // Hex colors
  if (/^#[0-9a-f]{3,8}$/i.test(lower)) return true;

  // Time formats
  if (/^\d{1,2}:\d{2}(:\d{2})?(\s*[ap]m)?$/.test(lower)) return true;

  // Percentage values
  if (/^[\d.]+%$/.test(lower)) return true;

  // Pixel/rem values
  if (/^[\d.]+(px|rem|em|vh|vw)$/.test(lower)) return true;

  // URLs
  if (/^https?:\/\//i.test(lower)) return true;

  // File paths
  if (/^\/[\w/.-]*$/.test(lower)) return true;
  if (/\.(png|jpg|jpeg|gif|svg|webp|ico|css|js|json|xml)$/i.test(lower)) return true;

  // Email addresses
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(lower)) return true;

  // Phone numbers
  if (/^[\d\s\-+()]{7,}$/.test(lower) && /\d{3,}/.test(lower) && /[+()\d]/.test(lower)) return true;

  // Single word CSS/JS identifiers
  const words = lower.split(/\s+/);

  // If it's a single word
    if (words.length === 1) {
      const w = words[0];
      // Skip single chars or single digits
      if (w.length === 1 && /[a-z\d]/.test(w)) return true;
      // Known non-translatable
      if (knownNonTranslatable.has(w)) return true;
      // Month names
      if (monthNames.has(w)) return true;
      // Kebab-case or underscore IDs (icon names like "add_a_photo", "lunch_dining")
      if (/^[a-z][\w-]*$/.test(w) && (w.includes('-') || w.includes('_'))) return true;
      // camelCase with uppercase inside
      if (/^[a-z]+[A-Z]/.test(w)) return true;
      // Numbers with suffixes
      if (/^\d/.test(w)) return true;
      // Random short words that are clearly code
      if (/^[a-z]{2,3}$/.test(w) && !/[aeiou]{2,}/.test(w) && !/^[aeiou]/.test(w)) return true;
      // Portuguese words
      if (ptWords.has(w)) return true;
    }

  // For multi-word strings
  if (words.length >= 2) {
    // Check if it's a collection of CSS classes or code identifiers
    const codeLikeWords = words.filter(w => {
      if (/^[a-z][\w-]*$/.test(w) && (w.includes('-') || /[A-Z]/.test(w))) return true;
      if (/^[\d,.\-+%$€£₦R$]+$/.test(w)) return true;
      if (/^[#@]/.test(w)) return true;
      if (/^\d/.test(w)) return true;
      if (knownNonTranslatable.has(w.toLowerCase())) return true;
      return false;
    });

    // If most words look like code, filter it out
    if (codeLikeWords.length >= words.length * 0.5) return true;

    // Check for Portuguese content
    const ptCount = countPortugueseWords(words);
    if (ptCount > 0 && ptCount >= words.length * 0.4) return true;
  }

  // Single word with no vowels is likely code
  if (words.length === 1 && !/[aeiou]/i.test(words[0]) && words[0].length > 1) return true;

  // Single-letter uppercase abbreviations (AA, AC, AK, EB, EK, UN, IG)
  if (words.length === 1 && /^[A-Z]{2}$/.test(s)) return true;
  // All-caps codes (3-6 letters) that aren't known acronyms (TUE, HIDDEN, STAGING)
  if (words.length === 1 && /^[A-Z]{3,6}$/.test(s) && !['ALL', 'ANY', 'DAY', 'FOR', 'GET', 'MAX', 'MIN', 'NEW', 'NOW', 'OLD', 'OUT', 'PER', 'TOP', 'VIA', 'DUE', 'USE', 'LIVE', 'COD', 'SLA', 'OTP', 'ROI', 'CAC', 'SKU', 'FAB', 'BOGO', 'VAT', 'API', 'AWS', 'URL', 'CSV', 'PDF', 'MTD', 'MOM'].includes(s)) return true;

  // Order line items: "1x Egusi Soup, 2x Pounded Yam"
  if (/^\d+x /.test(lower)) return true;

  // Must be English
  if (!isEnglishText(s) && !isMostlyEnglish(s)) return true;

  // Filter out generated AI image descriptions (they're typically long alt text)
  if (lower.length > 150 && /(lighting|background|palette|photography|aesthetic|atmosphere|mood|studio)/.test(lower)) return true;

  // Filter out strings that start with special chars that indicate they're part of a list/data display
  if (/^[#@]\w/.test(lower) && words.length <= 3) return true;

  // Filter out data-like strings (SKU, IDs)
  if (/^(sku|id|ref|ord|pay|trx|tk|vc|adj|tx)[:#\s-]+\w/i.test(lower.replace(/[^a-zA-Z0-9:]/g, ''))) return true;

  // Filter out strings with too many special chars
  const specialCount = (original.match(/[^a-zA-Z0-9\s\-'.,!?;:()&@#%$€£₦]/g) || []).length;
  if (specialCount > original.length * 0.3) return true;

  return false;
}

function main() {
  const files = getAllHTMLFiles(SCREENS_DIR);
  console.error(`Found ${files.length} HTML files`);

  const allTexts = new Set();

  for (const file of files) {
    const filePath = path.join(SCREENS_DIR, file);
    const html = fs.readFileSync(filePath, 'utf-8');
    const texts = extractTextNodes(html);

    for (const t of texts) {
      const cleaned = cleanText(t);
      if (!cleaned) continue;

      // Split by common delimiters to separate concatenated text
      const segments = cleaned.split(/\s{2,}|•|›|❯|\|/);
      for (const seg of segments) {
        const c = cleanText(seg);
        if (c && !shouldFilter(c)) {
          allTexts.add(c);
        }
      }
    }
  }

  const sorted = [...allTexts].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
  console.error(`Total unique strings: ${sorted.length}`);
  console.log(JSON.stringify(sorted, null, 2));
}

main();
