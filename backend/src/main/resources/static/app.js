window.CinemaStar = (function () {
    const api = (path, opts) => fetch(path, opts).then(async r => {
        const text = await r.text();
        let body;
        try { body = text ? JSON.parse(text) : {}; } catch { body = { raw: text }; }
        return { ok: r.ok, status: r.status, body };
    });

    function escapeHtml(s) {
        return String(s ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;' }[c]));
    }

    async function loadMovies() {
        const container = document.getElementById('movies');
        const r = await api('/api/movies');
        if (!r.ok) { container.textContent = 'Failed to load movies'; return; }
        container.innerHTML = r.body.map(m => `
            <div class="movie-card">
                <h3>${escapeHtml(m.title)}</h3>
                <div class="meta">${escapeHtml(m.durationMinutes)} min · ${escapeHtml(m.language || '')} · ${escapeHtml(m.rating || '')}</div>
                <div class="show-list" data-movie-id="${m.id}">Loading shows…</div>
            </div>`).join('');
        for (const card of container.querySelectorAll('.movie-card')) {
            const movieId = card.querySelector('.show-list').dataset.movieId;
            const sr = await api(`/api/movies/${movieId}/shows`);
            const target = card.querySelector('.show-list');
            if (!sr.ok || !sr.body.length) { target.textContent = 'No shows'; continue; }
            target.innerHTML = sr.body.map(s => `
                <a href="/seats.html?showId=${s.id}">${escapeHtml(new Date(s.startTime).toLocaleString())}</a>
            `).join('');
        }
    }

    async function loadSeatsPage() {
        const showId = new URLSearchParams(location.search).get('showId');
        if (!showId) { document.getElementById('seats').textContent = 'Missing showId'; return; }
        const r = await api(`/api/shows/${showId}/seats`);
        const info = document.getElementById('show-info');
        const grid = document.getElementById('seats');
        if (!r.ok) { grid.textContent = 'Failed'; return; }
        info.innerHTML = `<h2>Show #${showId}</h2><p>${r.body.seats.length} seats</p>`;
        grid.innerHTML = r.body.seats.map(s => {
            const cls = s.status.toLowerCase();
            const disabled = (cls !== 'available') ? 'disabled' : '';
            return `<button class="seat ${cls}" data-seat-code="${escapeHtml(s.seatCode)}" ${disabled}>${escapeHtml(s.seatCode)}</button>`;
        }).join('');
        let selectedCode = null;
        grid.querySelectorAll('.seat').forEach(btn => {
            btn.addEventListener('click', () => {
                if (btn.disabled) return;
                grid.querySelectorAll('.seat').forEach(b => b.classList.remove('selected'));
                btn.classList.add('selected');
                selectedCode = btn.dataset.seatCode;
                document.getElementById('checkout').hidden = false;
                document.getElementById('hold-btn').onclick = async () => {
                    const phone = document.getElementById('phone').value;
                    if (!phone) { alert('Enter phone'); return; }
                    const hr = await api(`/api/shows/${showId}/seats/${selectedCode}/hold`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ phone })
                    });
                    document.getElementById('hold-result').textContent = JSON.stringify(hr, null, 2);
                    if (hr.ok) {
                        sessionStorage.setItem('lastHold', JSON.stringify(hr.body));
                        setTimeout(() => location.href = `/checkout.html?bookingRef=${encodeURIComponent(hr.body.bookingRef)}`,
                                   800);
                    }
                };
            });
        });
    }

    async function loadCheckoutPage() {
        const bookingRef = new URLSearchParams(location.search).get('bookingRef');
        const stored = sessionStorage.getItem('lastHold');
        const hold = stored ? JSON.parse(stored) : null;
        const summary = document.getElementById('summary');
        summary.innerHTML = `<h2>Booking ${escapeHtml(bookingRef || '')}</h2>
            <p>Hold token: <code>${escapeHtml(hold?.holdToken?.slice(0,12) || '?') }…</code></p>
            <p>Amount: ${escapeHtml(hold?.amount || '?')} BDT</p>
            <p>Expires: ${escapeHtml(hold?.expiresAt || '?')}</p>`;
        document.getElementById('pay-btn').onclick = async () => {
            const phone = document.getElementById('phone').value;
            if (!phone || !hold?.holdToken) { alert('Missing phone or token'); return; }
            const r = await api(`/api/bookings/${bookingRef}/pay`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ holdToken: hold.holdToken, phone })
            });
            document.getElementById('pay-result').textContent = JSON.stringify(r, null, 2);
            if (r.ok) {
                pollStatus(bookingRef);
            }
        };
    }

    async function pollStatus(bookingRef) {
        const result = document.getElementById('pay-result');
        for (let i = 0; i < 30; i++) {
            await new Promise(r => setTimeout(r, 1500));
            const r = await api(`/api/bookings/${bookingRef}`);
            if (r.ok) {
                result.textContent += `\n\n[${i+1}] Status: ${r.body.status}`;
                if (r.body.status === 'CONFIRMED' || r.body.status === 'REFUNDED' || r.body.status === 'PAYMENT_FAILED' || r.body.status === 'EXPIRED') {
                    return;
                }
            }
        }
        result.textContent += '\n\nTimed out waiting for confirmation.';
    }

    return { loadMovies, loadSeatsPage, loadCheckoutPage };
})();