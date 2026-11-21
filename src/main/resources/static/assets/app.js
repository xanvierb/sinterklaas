const preference = window.matchMedia('(prefers-color-scheme: dark)');

function applyColorMode() {
    const theme = document.documentElement.dataset;
    theme.bsTheme = theme.colorMode === 'DARK' || (theme.colorMode === 'SYSTEM' && preference.matches)
        ? 'dark' : 'light';
}

applyColorMode();
preference.addEventListener('change', applyColorMode);

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-shop-icon]').forEach(icon => {
        const showIcon = () => { icon.hidden = icon.naturalWidth === 0; };
        icon.addEventListener('load', showIcon);
        if (icon.complete) showIcon();
    });
    const token = document.querySelector('#token');
    if (token && /^#[A-Za-z0-9_-]{43}$/.test(location.hash)) {
        token.value = location.hash.slice(1);
        history.replaceState(null, '', location.pathname + location.search);
    }
    const copy = document.querySelector('[data-copy-invite]');
    if (copy && navigator.clipboard) {
        copy.hidden = false;
        copy.addEventListener('click', async () => {
            const field = document.querySelector('#invite-link');
            try {
                await navigator.clipboard.writeText(field.value);
                document.querySelector('#copy-result').textContent = 'Link gekopieerd.';
            } catch {
                field.select();
            }
        });
    }
});

function showWishDetails(form) {
    const details = form.querySelector('[data-wish-details]');
    if (details.dataset.wishWizard !== 'true') return;
    details.hidden = false;
    details.disabled = false;
    form.querySelector('[data-wish-link-step]').hidden = true;
    form.querySelector('[data-wish-cancel]').hidden = true;
    form.querySelector('[data-wish-bookmark]').closest('details').hidden = true;
    form.elements.title.focus();
}

document.addEventListener('DOMContentLoaded', () => {
    const button = document.querySelector('[data-fetch-product]');
    if (!button) return;

    const form = button.closest('form');
    const urlField = form.elements.productUrl;
    const message = form.querySelector('[data-fetch-message]');
    const details = form.querySelector('[data-wish-details]');
    const wizard = details.dataset.wishWizard === 'true';

    if (wizard) {
        details.disabled = true;
        details.hidden = true;
        const skip = form.querySelector('[data-wish-skip]');
        skip.hidden = false;
        form.querySelector('[data-wish-cancel]').hidden = false;
        skip.addEventListener('click', () => {
            if (button.disabled) return;
            urlField.value = '';
            message.textContent = '';
            showWishDetails(form);
        });
        const store = form.querySelector('[data-wish-store]');
        store.hidden = false;
        store.addEventListener('click', () => {
            if (button.disabled) return;
            form.querySelector('#inStore').checked = true;
            urlField.value = '';
            message.textContent = '';
            showWishDetails(form);
        });
        urlField.addEventListener('keydown', event => {
            if (event.key !== 'Enter') return;
            event.preventDefault();
            button.click();
        });
    }

    button.addEventListener('click', async () => {
        if (!urlField.value.trim()) {
            message.textContent = 'Vul eerst een productlink in.';
            urlField.focus();
            return;
        }
        if (!urlField.reportValidity()) return;

        button.disabled = true;
        message.textContent = 'Productgegevens ophalen…';
        try {
            const body = new URLSearchParams({productUrl: urlField.value.trim()});
            const csrf = form.querySelector('input[name="_csrf"]');
            if (csrf) body.set(csrf.name, csrf.value);
            const response = await fetch('/wensen/productgegevens', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body
            });
            if (!response.ok) throw new Error();
            const data = await response.json();
            fillIfEmpty(form, 'title', data.title);
            fillIfEmpty(form, 'description', data.description);
            fillIfEmpty(form, 'estimatedPrice', data.price);
            const imageAttached = await attachDownloadedImage(form, data);
            message.textContent = imageAttached
                ? 'Gegevens opgehaald en de productfoto is toegevoegd. Sla de wens op om de foto te bewaren.'
                : 'Gegevens opgehaald. Controleer ze en pas ze zo nodig aan.';
        } catch {
            message.textContent = 'De productgegevens konden niet worden opgehaald. Vul de wens zelf verder in.';
        } finally {
            showWishDetails(form);
            button.disabled = false;
        }
    });
});

function fillIfEmpty(form, name, value) {
    const field = form.querySelector('[name="' + name + '"]');
    if (field && !field.value.trim() && value) field.value = value;
}

const modernPhotoType = /^(image\/(webp|avif))$/;

async function convertPhotoToPng(photo) {
    const bitmap = await createImageBitmap(photo);
    try {
        const scale = Math.min(1, 2400 / bitmap.width, 2400 / bitmap.height,
            Math.sqrt(4000000 / (bitmap.width * bitmap.height)));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.floor(bitmap.width * scale));
        canvas.height = Math.max(1, Math.floor(bitmap.height * scale));
        canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        const png = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
        if (!png) throw new Error('PNG conversion failed');
        return new File([png], photo.name.replace(/\.[^.]+$/, '') + '.png', {type: 'image/png'});
    } finally {
        bitmap.close();
    }
}

function setPhotoFile(input, file) {
    const transfer = new DataTransfer();
    transfer.items.add(file);
    input.files = transfer.files;
}

document.addEventListener('change', async event => {
    const input = event.target;
    if (!input.matches('[data-photo-upload]')) return;
    input.setCustomValidity('');
    const photo = input.files[0];
    if (!photo || (!modernPhotoType.test(photo.type) && !/\.(avif|webp)$/i.test(photo.name))) return;

    input.setCustomValidity('De foto wordt voorbereid. Probeer het zo opnieuw.');
    try {
        const png = await convertPhotoToPng(photo);
        if (input.files[0] !== photo) return;
        setPhotoFile(input, png);
        input.setCustomValidity('');
    } catch {
        if (input.files[0] !== photo) return;
        input.setCustomValidity('Deze foto kon niet worden gelezen. Kies een andere foto.');
        input.reportValidity();
    }
});

async function attachDownloadedImage(form, data) {
    if (!data.imageBase64 || !data.imageContentType) return false;
    const input = form.querySelector('[name="photo"]');
    if (!input || input.files.length > 0) return false;
    const extensions = {'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp', 'image/avif': 'avif'};
    const extension = extensions[data.imageContentType];
    if (!extension) return false;

    const binary = atob(data.imageBase64);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
    let file = new File([bytes], 'product-image.' + extension, {type: data.imageContentType});
    if (modernPhotoType.test(file.type)) {
        try {
            file = await convertPhotoToPng(file);
        } catch {
            return false;
        }
    }
    const previewUrl = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
    if (input.files.length > 0) return false;
    setPhotoFile(input, file);
    const preview = form.querySelector('[data-wish-photo-preview]');
    preview.src = previewUrl;
    preview.hidden = false;
    input.addEventListener('change', () => {
        preview.hidden = true;
        preview.removeAttribute('src');
    }, {once: true});
    return true;
}

// Included in the bookmark as well as used when importing saved HTML.
function readEuroPrice(root) {
    function normalize(raw, currency) {
        if (currency && String(currency).trim().toUpperCase() !== 'EUR') return '';
        let value = String(raw ?? '').trim().replace(/^(?:€|EUR)\s*|\s*(?:€|EUR)$/gi, '').trim();
        value = value.replace(/[.,][-–]$/, '.00');
        // Strip a thousands separator only when the groups form a complete amount.
        if (/^\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?$/.test(value)) value = value.replaceAll('.', '');
        else if (/^\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?$/.test(value)) value = value.replaceAll(',', '');
        value = value.replace(',', '.');
        return /^\d{1,8}(?:\.\d{1,2})?$/.test(value) ? value : '';
    }
    function content(element) {
        return element?.getAttribute('content') || element?.getAttribute('value') || element?.textContent || '';
    }
    const currency = content(root.querySelector('[itemprop="priceCurrency"],[property="product:price:currency"],[property="og:price:currency"]'));
    for (const selector of ['[property="product:price:amount"]', '[property="og:price:amount"]', '[itemprop="price"]']) {
        const amount = normalize(content(root.querySelector(selector)), currency);
        if (amount) return amount;
    }
    for (const script of root.querySelectorAll('script[type="application/ld+json"]')) {
        let data;
        try { data = JSON.parse(script.textContent); } catch { continue; }
        const nodes = [data].flat().flatMap(node => [node, ...[node?.['@graph'] || []].flat()]);
        for (const node of nodes) {
            if (![node?.['@type']].flat().includes('Product')) continue;
            for (const offer of [node.offers].flat()) {
                if (!offer) continue;
                const amount = normalize(offer.price ?? offer.priceSpecification?.price ?? offer.lowPrice,
                    offer.priceCurrency || offer.priceSpecification?.priceCurrency || currency);
                if (amount) return amount;
            }
        }
    }
    for (const selector of ['.a-price .a-offscreen', '.product-price', '.price-new', '.current-price', '.price']) {
        const amount = normalize(content(root.querySelector(selector)), currency);
        if (amount) return amount;
    }
    return '';
}

// This function also runs as a bookmark on the product page. Keep it self-contained.
function readProduct(root, url, pageTitle) {
    const meta = selector => root.querySelector(selector)?.getAttribute('content') || '';
    const title = meta('meta[property="og:title"]') || root.querySelector('h1')?.textContent || pageTitle;
    const description = meta('meta[property="og:description"]') || meta('meta[name="description"]');
    const image = meta('meta[property="og:image"]') || root.querySelector('[itemprop="image"]')?.getAttribute('src');
    let imageUrl = '';
    if (image) {
        try { imageUrl = new URL(image, url).href; } catch { /* Optional image. */ }
    }
    return { title: title.trim().slice(0, 120), description: description.slice(0, 2000),
        price: readEuroPrice(root),
        productUrl: url, imageUrl };
}

document.addEventListener('DOMContentLoaded', () => {
    const bookmark = document.querySelector('[data-wish-bookmark]');
    if (!bookmark) return;
    const form = bookmark.closest('form');
    const message = form.querySelector('[data-fetch-message]');
    const destination = JSON.stringify(new URL('/wensen/nieuw', location.origin).href);
    bookmark.href = 'javascript:' + encodeURIComponent('void (()=>{const readEuroPrice=' + readEuroPrice.toString() + ';const data=(' + readProduct.toString() +
        ')(document,location.href,document.title);window.open(' + destination +
        '+"#product="+encodeURIComponent(JSON.stringify(data)),"_blank","noopener");})()');
    bookmark.addEventListener('click', event => {
        event.preventDefault();
        message.textContent = 'Sleep de bladwijzer naar je bladwijzerbalk en gebruik hem op de productpagina.';
    });

    async function importProduct(data) {
        const validLink = value => {
            try {
                const link = new URL(value);
                return ['https:', 'http:'].includes(link.protocol) && !link.username && !link.password;
            } catch { return false; }
        };
        if (!validLink(data.productUrl) || !data.title) throw new Error();
        fillIfEmpty(form, 'productUrl', data.productUrl);
        fillIfEmpty(form, 'title', data.title);
        fillIfEmpty(form, 'description', data.description);
        fillIfEmpty(form, 'estimatedPrice', data.price);
        showWishDetails(form);
        const summary = form.elements.estimatedPrice.value
            ? 'Productgegevens overgenomen.'
            : 'Productgegevens overgenomen. Geen europrijs gevonden op de pagina.';
        message.textContent = summary;
        if (!validLink(data.imageUrl) || form.elements.photo.files.length) return;

        const csrf = form.querySelector('input[name="_csrf"]');
        const submit = form.querySelector('.actions button');
        submit.disabled = true;
        message.textContent = summary + ' Productfoto downloaden…';
        try {
            const body = new URLSearchParams({imageUrl: data.imageUrl});
            if (csrf) body.set(csrf.name, csrf.value);
            const response = await fetch('/wensen/productfoto', {method: 'POST', body});
            if (!response.ok) throw new Error();
            const attached = await attachDownloadedImage(form, await response.json());
            message.textContent = summary + (attached ? ' Productfoto toegevoegd.'
                : form.elements.photo.files.length ? '' : ' De productfoto kon niet worden toegevoegd.');
        } catch {
            message.textContent = summary + ' De productfoto kon niet worden toegevoegd.';
        } finally {
            submit.disabled = false;
        }
    }
    if (location.hash.startsWith('#product=')) {
        const payload = location.hash.slice(9);
        history.replaceState(null, '', location.pathname + location.search);
        const reportError = () => { message.textContent = 'De productgegevens konden niet worden gelezen.'; };
        try {
            const data = JSON.parse(decodeURIComponent(payload));
            const chooser = document.getElementById('bookmark-wishlist');
            if (!chooser) {
                importProduct(data).catch(reportError);
            } else {
                chooser.returnValue = 'cancel';
                chooser.addEventListener('close', () => {
                    const choice = [...chooser.querySelectorAll('[data-list-name]')]
                        .find(button => button.value === chooser.returnValue);
                    if (!choice) return;
                    form.elements.owner.value = choice.value === 'own' ? '' : choice.value;
                    const badge = form.querySelector('[data-bookmark-list]');
                    badge.textContent = choice.dataset.listName;
                    badge.hidden = false;
                    importProduct(data).catch(reportError);
                }, {once: true});
                chooser.showModal();
            }
        } catch {
            reportError();
        }
    }
    form.querySelector('#saved-page').addEventListener('change', async event => {
        const file = event.target.files[0];
        if (!file) return;
        if (file.size > 5000000) {
            message.textContent = 'Kies een HTML-bestand van maximaal 5 MB.';
            return;
        }
        try {
            const template = document.createElement('template');
            template.innerHTML = await file.text();
            const root = template.content;
            const url = form.elements.productUrl.value || root.querySelector('link[rel="canonical"]')?.getAttribute('href');
            await importProduct(readProduct(root, url, root.querySelector('title')?.textContent || ''));
        } catch { message.textContent = 'Controleer de productlink en kies een opgeslagen productpagina.'; }
        finally { event.target.value = ''; }
    });
});

document.addEventListener('DOMContentLoaded', () => {
    const messages = document.getElementById('chat-messages');
    if (!messages) return;
    messages.scrollTop = messages.scrollHeight;

    const compose = document.querySelector('.chat-compose');
    compose?.querySelector('textarea')?.addEventListener('keydown', event => {
        if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
        event.preventDefault();
        if (!event.repeat && event.currentTarget.value.trim()) compose.requestSubmit();
    });

    let scrollTop = 0;
    let atBottom = true;
    messages.addEventListener('htmx:beforeSwap', () => {
        scrollTop = messages.scrollTop;
        atBottom = messages.scrollHeight - messages.clientHeight - scrollTop < 32;
    });
    messages.addEventListener('htmx:afterSwap', () => {
        messages.scrollTop = atBottom ? messages.scrollHeight : scrollTop;
    });
});

document.addEventListener('DOMContentLoaded', () => {
    const editor = document.getElementById('gift-time-editor');
    if (!editor) return;
    const form = editor.querySelector('form');
    document.querySelectorAll('[data-edit-gift-time]').forEach(button => {
        button.addEventListener('click', () => {
            form.action = button.dataset.action;
            form.elements.namedItem('step').value = button.dataset.step;
            form.elements.namedItem('time').value = button.dataset.time;
            editor.showModal();
        });
    });
    editor.querySelector('[data-close-gift-time]').addEventListener('click', () => editor.close());
});

document.addEventListener('DOMContentLoaded', () => {
    const path = window.location.pathname;
    const navPath = /^\/(reserveringen|wensen)\/[^/]+\/chat$/.test(path) ? '/chats' : path;
    for (const nav of document.querySelectorAll('.site-nav, .admin-nav')) {
        const links = [...nav.querySelectorAll('a')];
        const active = links.find(link => new URL(link.href).pathname === navPath)
            ?? links.filter(link => {
                const target = new URL(link.href).pathname;
                return target !== '/' && navPath.startsWith(target + '/');
            }).sort((first, second) => second.pathname.length - first.pathname.length)[0];
        if (active) {
            active.setAttribute('aria-current', active.pathname === path ? 'page' : 'location');
            active.closest('.nav-group')?.classList.add('is-current');
        }
    }

    document.addEventListener('click', event => {
        document.querySelectorAll('.nav-group[open]').forEach(group => {
            if (!group.contains(event.target)) group.open = false;
        });
    });
    document.addEventListener('keydown', event => {
        if (event.key !== 'Escape') return;
        const group = event.target.closest('.nav-group[open]');
        if (!group) return;
        group.open = false;
        group.querySelector('summary').focus();
    });
});

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-copy-invite-url]').forEach(form => {
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const status = form.querySelector('[data-invite-copy-status]');
            const fallback = form.querySelector('[data-invite-copy-fallback]');
            const button = form.querySelector('button');
            fallback.hidden = true;
            fallback.value = '';
            button.disabled = true;
            status.textContent = '';
            try {
                const response = await fetch(form.dataset.copyInviteUrl, {
                    method: 'POST',
                    body: new URLSearchParams(new FormData(form)),
                    redirect: 'error'
                });
                if (!response.ok) throw new Error();
                const link = await response.text();
                try {
                    await navigator.clipboard.writeText(link);
                    status.textContent = 'Link gekopieerd.';
                } catch {
                    fallback.value = link;
                    fallback.hidden = false;
                    fallback.select();
                    status.textContent = 'Kopieer de geselecteerde link.';
                }
            } catch {
                status.textContent = 'De link kon niet worden opgehaald.';
            } finally {
                button.disabled = false;
            }
        });
    });
});

document.addEventListener('DOMContentLoaded', () => {
    const toolbar = document.querySelector('[data-agreement-toolbar]');
    if (!toolbar) return;
    const field = toolbar.closest('form').elements.agreement;
    const formats = {
        bold: ['**', '**', 'vet'],
        italic: ['*', '*', 'cursief'],
        underline: ['++', '++', 'onderstreept'],
        link: ['[', '](https://voorbeeld.nl)', 'linktekst']
    };

    toolbar.hidden = false;
    toolbar.addEventListener('click', event => {
        const button = event.target.closest('[data-format]');
        if (!button) return;
        const [prefix, suffix, placeholder] = formats[button.dataset.format];
        const {selectionStart: start, selectionEnd: end, value} = field;
        const text = value.slice(start, end) || placeholder;
        const formatted = prefix + text + suffix;
        if (value.length - (end - start) + formatted.length > field.maxLength) {
            field.focus();
            return;
        }
        field.setRangeText(formatted, start, end);
        field.focus();
        if (button.dataset.format === 'link') {
            field.setSelectionRange(start + prefix.length + text.length + 2, start + formatted.length - 1);
        } else {
            field.setSelectionRange(start + prefix.length, start + prefix.length + text.length);
        }
    });
});

document.addEventListener('DOMContentLoaded', () => {
    const panel = document.querySelector('.overview-agreements');
    if (!panel) return;

    const summary = panel.querySelector('summary');
    const content = panel.querySelector('.overview-agreements-reveal');
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let animation;
    let expanding;

    summary.addEventListener('click', event => {
        const expand = animation ? !expanding : !panel.open;
        if (reducedMotion.matches || typeof content.animate !== 'function') {
            if (animation) {
                event.preventDefault();
                animation.cancel();
                animation = null;
                panel.open = expand;
            }
            return;
        }

        event.preventDefault();
        const start = panel.open ? content.getBoundingClientRect().height : 0;
        animation?.cancel();
        panel.open = true;
        const end = expand ? content.getBoundingClientRect().height : 0;
        const current = content.animate([
            {height: start + 'px', overflow: 'hidden'},
            {height: end + 'px', overflow: 'hidden'}
        ], {duration: 280, easing: 'ease-in-out', fill: 'forwards'});
        expanding = expand;
        animation = current;
        current.onfinish = () => {
            if (animation !== current) return;
            panel.open = expand;
            current.cancel();
            animation = null;
        };
    });
});
