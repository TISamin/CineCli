/* CinemaSeat — frontend logic. Vanilla JS, no build step. */

const CinemaSeat = (function () {
    const API_BASE = '';

    // ---------------- HTTP helpers ----------------
    async function api(path, opts = {}) {
        const init = {
            headers: { 'Content-Type': 'application/json' },
            ...opts,
        };
        if (init.body && typeof init.body !== 'string') init.body = JSON.stringify(init.body);
        try {
            const r = await fetch(API_BASE + path, init);
            const text = await r.text();
            let body;
            try { body = text ? JSON.parse(text) : null; } catch { body = { raw: text }; }
            return { ok: r.ok, status: r.status, body };
        } catch (e) {
            return { ok: false, status: 0, body: { error: 'NETWORK', message: e.message } };
        }
    }

    // ---------------- UI helpers ----------------
    function $(sel, root = document) { return root.querySelector(sel); }
    function $$(sel, root = document) { return Array.from(root.querySelectorAll(sel)); }

    function escapeHtml(s) {
        return String(s ?? '').replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    function showToast(msg, type = 'info', durationMs = 4000) {
        const t = $('#toast');
        if (!t) return;
        t.className = 'toast ' + type;
        t.textContent = msg;
        t.hidden = false;
        clearTimeout(t._timer);
        t._timer = setTimeout(() => { t.hidden = true; }, durationMs);
    }

    function statusPill(status) {
        const cls = (status || '').toLowerCase();
        return `<span class="status-pill ${cls}">${escapeHtml(status || '—')}</span>`;
    }

    function fmtDateTime(iso) {
        if (!iso) return '—';
        try {
            const d = new Date(iso);
            return d.toLocaleString(undefined, {
                weekday: 'short', month: 'short', day: 'numeric',
                hour: '2-digit', minute: '2-digit',
            });
        } catch { return iso; }
    }

    function fmtTime(iso) {
        if (!iso) return '—';
        try {
            return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
        } catch { return iso; }
    }

    // ---------------- Movies page ----------------
    async function initMoviesPage() {
        const container = $('#movies');
        const r = await api('/api/movies');
        if (!r.ok) {
            container.innerHTML = `<div class="loading">Failed to load movies: ${escapeHtml(r.body?.message || r.status)}</div>`;
            return;
        }
        if (!r.body.length) {
            container.innerHTML = `<div class="loading">No movies are showing right now.</div>`;
            return;
        }
        container.innerHTML = r.body.map(m => `
            <div class="movie-card">
                <h3>${escapeHtml(m.title)}</h3>
                <div class="meta">${escapeHtml(m.durationMinutes)} min · ${escapeHtml(m.language || '')} · ${escapeHtml(m.rating || '')}</div>
                <div class="desc">${escapeHtml(m.description || '')}</div>
                <div class="shows" data-movie-id="${m.id}"><span class="muted">Loading showtimes…</span></div>
            </div>
        `).join('');

        for (const card of container.querySelectorAll('.movie-card')) {
            const movieId = card.querySelector('.shows').dataset.movieId;
            const sr = await api(`/api/movies/${movieId}/shows`);
            const target = card.querySelector('.shows');
            if (!sr.ok || !sr.body.length) {
                target.innerHTML = `<span class="muted">No upcoming shows</span>`;
                continue;
            }
            target.innerHTML = sr.body.map(s => `
                <a href="/seats.html?showId=${s.id}">${escapeHtml(fmtDateTime(s.startTime))}</a>
            `).join('');
        }
    }

    // ---------------- Seats page ----------------
    async function initSeatsPage() {
        const params = new URLSearchParams(location.search);
        const showId = params.get('showId');
        if (!showId) { $('#show-meta').innerHTML = '<p>Missing showId</p>'; return; }

        // Load show metadata
        const showR = await api(`/api/shows/${showId}`);
        if (showR.ok) {
            const s = showR.body;
            $('#show-meta').innerHTML = `
                <h2>${escapeHtml(s.movieTitle || ('Show #' + showId))}</h2>
                <div class="meta-item"><strong>Starts:</strong>${escapeHtml(fmtDateTime(s.startTime))}</div>
                <div class="meta-item"><strong>Ends:</strong>${escapeHtml(fmtTime(s.endTime))}</div>
                <div class="meta-item"><strong>Screen:</strong>${escapeHtml(s.screenName || '—')}</div>
            `;
        } else {
            $('#show-meta').innerHTML = `<h2>Show #${showId}</h2>`;
        }

        // Load seat map
        await loadSeatMap(showId);

        // Bind hold button
        $('#hold-btn').addEventListener('click', () => holdSelectedSeats(showId));
    }

    async function loadSeatMap(showId) {
        const grid = $('#seats');
        grid.innerHTML = '<div class="loading"><span class="spinner"></span>Loading seats…</div>';
        const r = await api(`/api/shows/${showId}/seats`);
        if (!r.ok) {
            grid.innerHTML = `<div class="loading">Failed to load seats</div>`;
            return;
        }
        const seats = r.body.seats || [];
        renderSeatGrid(seats);
    }

    function renderSeatGrid(seats) {
        const grid = $('#seats');
        grid.className = 'seat-grid';
        grid.innerHTML = seats.map(s => {
            const status = (s.status || 'available').toLowerCase();
            const disabled = status !== 'available' ? 'disabled' : '';
            return `<button type="button" class="seat ${status}" data-seat-id="${s.showSeatId}" data-seat-code="${escapeHtml(s.seatCode)}" data-price="${s.price}" ${disabled}>${escapeHtml(s.seatCode)}</button>`;
        }).join('');

        $$('.seat', grid).forEach(btn => {
            btn.addEventListener('click', () => toggleSeat(btn));
        });
        updateSummary();
    }

    function toggleSeat(btn) {
        if (btn.disabled) return;
        btn.classList.toggle('selected');
        updateSummary();
    }

    function getSelectedSeats() {
        return $$('.seat.selected').map(b => ({
            showSeatId: Number(b.dataset.seatId),
            seatCode: b.dataset.seatCode,
            price: Number(b.dataset.price),
        }));
    }

    function updateSummary() {
        const selected = getSelectedSeats();
        const summary = $('#summary-bar');
        const info = $('#summary-info');
        const btn = $('#hold-btn');
        if (selected.length === 0) {
            info.innerHTML = `<div class="empty">Select up to 6 seats to continue</div>`;
            btn.disabled = true;
        } else {
            const codes = selected.map(s => s.seatCode).join(', ');
            const total = selected.reduce((sum, s) => sum + s.price, 0);
            info.innerHTML = `
                <div class="seats-chosen">${escapeHtml(codes)}</div>
                <div class="price">${selected.length} seat${selected.length > 1 ? 's' : ''} · ${total.toFixed(2)} BDT</div>
            `;
            btn.disabled = false;
        }
    }

    async function holdSelectedSeats(showId) {
        const selected = getSelectedSeats();
        if (selected.length === 0) return;
        const phone = $('#phone').value.trim();
        if (!phone) { showToast('Enter your phone number', 'error'); return; }

        const btn = $('#hold-btn');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span>Holding…';

        const holds = [];
        for (const seat of selected) {
            const r = await api(`/api/shows/${showId}/seats/${seat.seatCode}/hold`, {
                method: 'POST',
                body: { phone },
            });
            if (!r.ok) {
                // Rollback any successful holds so far
                showToast(`Failed to hold ${seat.seatCode}: ${r.body?.message || r.status}`, 'error', 6000);
                btn.disabled = false;
                btn.textContent = 'Hold seats';
                // Refresh seat map so the user sees which were taken
                await loadSeatMap(showId);
                return;
            }
            holds.push({ ...r.body, seatCode: seat.seatCode, price: seat.price });
        }

        // Store and go to checkout
        sessionStorage.setItem('cinemaseat_checkout', JSON.stringify({
            holds,
            phone,
        }));
        location.href = '/checkout.html';
    }

    // ---------------- Checkout page ----------------
    async function initCheckoutPage() {
        const stored = sessionStorage.getItem('cinemaseat_checkout');
        if (!stored) {
            $('#checkout-card').innerHTML = `
                <h2>No active booking</h2>
                <p class="muted">Start a booking from the <a href="/">movies page</a>.</p>
            `;
            return;
        }
        const { holds, phone } = JSON.parse(stored);
        const total = holds.reduce((sum, h) => sum + Number(h.price || 0), 0);
        const codes = holds.map(h => h.seatCode).join(', ');

        const card = $('#checkout-card');
        card.innerHTML = `
            <h2>Review &amp; Pay</h2>
            <div class="checkout-row">
                <span class="label">Seats</span>
                <span class="value">${escapeHtml(codes)}</span>
            </div>
            <div class="checkout-row">
                <span class="label">Phone</span>
                <span class="value">${escapeHtml(phone)}</span>
            </div>
            <div class="checkout-row">
                <span class="label">Booking ref</span>
                <span class="value"><code>${escapeHtml(holds[0].bookingRef.slice(0, 18))}…</code></span>
            </div>
            <div class="checkout-row">
                <span class="label">Hold expires</span>
                <span class="value">${escapeHtml(fmtDateTime(holds[0].expiresAt))}</span>
            </div>
            <div class="checkout-row">
                <span class="label">Total</span>
                <span class="value"><strong>${total.toFixed(2)} BDT</strong></span>
            </div>

            <label>
                <span>Confirm phone</span>
                <input type="tel" id="phone" value="${escapeHtml(phone)}" />
            </label>

            <div class="checkout-actions">
                <button id="pay-btn">Pay now</button>
                <a href="/" class="btn secondary">Cancel</a>
            </div>

            <div id="pay-status" hidden></div>
        `;

        $('#pay-btn').addEventListener('click', () => doPay(holds));
    }

    async function doPay(holds) {
        const phone = $('#phone').value.trim();
        if (!phone) { showToast('Enter your phone number', 'error'); return; }

        const btn = $('#pay-btn');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span>Charging…';

        // Pay for each hold (gateway keys are per-booking-ref)
        for (const h of holds) {
            const r = await api(`/api/bookings/${h.bookingRef}/pay`, {
                method: 'POST',
                body: { holdToken: h.holdToken, phone },
            });
            if (!r.ok) {
                btn.disabled = false;
                btn.textContent = 'Pay now';
                showToast(`Pay failed for ${h.seatCode}: ${r.body?.message || r.status}`, 'error');
                return;
            }
        }

        // Now wait for async confirmation; show busy status
        const status = $('#pay-status');
        status.hidden = false;
        status.innerHTML = `
            <div class="checkout-card" style="margin-top: 16px;">
                <p><span class="spinner"></span>Waiting for payment confirmation…</p>
                <p class="muted" id="status-detail">Gateway is processing your payment.</p>
            </div>
        `;

        // Show OTP step (409 may have been sent but we still try to verify)
        // Some integrations require OTP verification per booking; we expose it as an optional step.
        setTimeout(() => {
            status.innerHTML += `
                <div class="checkout-card" style="margin-top: 16px;">
                    <p>OTP verification (optional)</p>
                    <label><span>Enter the code sent to your phone (deterministic mode: 123456)</span></span>
                        <div class="otp-row">
                            <input type="text" id="otp-code" maxlength="6" placeholder="000000" />
                            <button id="otp-btn" class="secondary">Verify</button>
                        </div>
                    </label>
                    <div class="help">In deterministic mode the OTP is <code>123456</code>. Use <code>/debug/otp/:ref</code> on the gateway to inspect.</div>
                    <div id="otp-result"></div>
                </div>
            `;
            $('#otp-btn').addEventListener('click', () => doOtp(holds[0].bookingRef));
        }, 1500);

        // Poll the booking status
        pollAllBookings(holds);
    }

    async function doOtp(bookingRef) {
        const code = $('#otp-code').value.trim();
        const result = $('#otp-result');
        if (!code) { showToast('Enter the OTP code', 'error'); return; }
        const r = await api('/api/otp/verify', {
            method: 'POST',
            body: { ref: bookingRef, code },
        });
        if (r.ok) {
            result.innerHTML = `<p class="muted">OTP verified. ${r.body.remainingAttempts} attempts remaining.</p>`;
        } else {
            result.innerHTML = `<p style="color: var(--red);">${escapeHtml(r.body?.message || 'Verification failed')}</p>`;
        }
    }

    async function pollAllBookings(holds) {
        const detail = $('#status-detail');
        for (let i = 0; i < 30; i++) {
            await new Promise(r => setTimeout(r, 1500));
            // Check all holds; if any is final, redirect to confirmation
            const results = await Promise.all(holds.map(h => api(`/api/bookings/${h.bookingRef}`)));
            const summary = results.map((r, idx) => {
                const status = r.ok ? r.body.status : 'ERROR';
                return `${holds[idx].seatCode}: ${status}`;
            }).join('  ·  ');
            if (detail) detail.textContent = summary;

            const all = results.every(r => r.ok && ['CONFIRMED', 'REFUNDED', 'PAYMENT_FAILED', 'EXPIRED'].includes(r.body.status));
            if (all) {
                // Save final state and redirect
                const final = results.map(r => r.body);
                sessionStorage.setItem('cinemaseat_final', JSON.stringify(final));
                location.href = '/confirmation.html';
                return;
            }
        }
        if (detail) detail.textContent = 'Timed out waiting for confirmation. Refresh or check the booking ref.';
    }

    // ---------------- Confirmation page ----------------
    async function initConfirmationPage() {
        const stored = sessionStorage.getItem('cinemaseat_final') || sessionStorage.getItem('cinemaseat_checkout');
        if (!stored) {
            $('#confirmation').innerHTML = `
                <h2>No booking</h2>
                <p class="muted"><a href="/">Back to movies</a></p>
            `;
            return;
        }
        const bookings = JSON.parse(stored);
        const arr = Array.isArray(bookings) ? bookings : [bookings];
        const stored2 = sessionStorage.getItem('cinemaseat_checkout');
        const holds = stored2 ? JSON.parse(stored2).holds : [];
        const seatByRef = {};
        holds.forEach(h => { seatByRef[h.bookingRef] = h.seatCode; });

        const allOk = arr.every(b => b.status === 'CONFIRMED');
        const anyFailed = arr.some(b => b.status === 'PAYMENT_FAILED' || b.status === 'EXPIRED');
        const anyRefunded = arr.some(b => b.status === 'REFUNDED');
        const allConfirmed = arr.every(b => b.status === 'CONFIRMED');

        const cls = anyFailed ? 'failed' : (anyRefunded ? 'pending' : 'confirmation');
        const icon = anyFailed ? '✗' : (anyRefunded ? '↩' : '✓');
        const title = anyFailed ? 'Booking Failed' : (anyRefunded ? 'Refund Processed' : 'Booking Confirmed');
        const subtitle = anyFailed
            ? 'One or more seats could not be booked. The hold was released.'
            : (anyRefunded ? 'Your refund has been processed and the seat is released.' : 'Your seats are reserved. Enjoy the show!');

        const total = arr.reduce((sum, b) => sum + Number(b.totalAmount || 0), 0);

        $('#confirmation').className = 'confirmation ' + cls;
        $('#confirmation').innerHTML = `
            <div class="icon">${icon}</div>
            <h2>${title}</h2>
            <p class="muted">${subtitle}</p>
            <div class="booking-ref">${arr.length} booking${arr.length > 1 ? 's' : ''} · ${total.toFixed(2)} BDT</div>

            <div style="text-align: left; margin-top: 24px;">
                ${arr.map(b => `
                    <div class="checkout-row">
                        <span class="label">${escapeHtml(seatByRef[b.bookingRef] || '?')} · <code>${escapeHtml(b.bookingRef.slice(0, 14))}…</code></span>
                        <span class="value">${statusPill(b.status)}</span>
                    </div>
                `).join('')}
            </div>

            <div class="checkout-actions" style="justify-content: center; margin-top: 24px;">
                ${allConfirmed ? `<button id="refund-btn" class="danger">Request refund</button>` : ''}
                <a href="/" class="btn">Book more</a>
            </div>

            <div id="refund-result" style="margin-top: 16px;"></div>
        `;

        const refundBtn = $('#refund-btn');
        if (refundBtn) {
            refundBtn.addEventListener('click', () => doRefund(arr));
        }
    }

    async function doRefund(bookings) {
        const result = $('#refund-result');
        const btn = $('#refund-btn');
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner"></span>Refunding…';
        for (const b of bookings) {
            const r = await api(`/api/bookings/${b.bookingRef}/refund`, { method: 'POST' });
            if (!r.ok) {
                result.innerHTML = `<p style="color: var(--red);">Refund failed for ${escapeHtml(b.bookingRef.slice(0, 14))}: ${escapeHtml(r.body?.message || '')}</p>`;
                btn.disabled = false;
                btn.textContent = 'Request refund';
                return;
            }
        }
        result.innerHTML = `<p class="muted">Refund requested. Waiting for gateway confirmation…</p>`;
        // Poll until all REFUNDED
        for (let i = 0; i < 30; i++) {
            await new Promise(r => setTimeout(r, 1500));
            const results = await Promise.all(bookings.map(b => api(`/api/bookings/${b.bookingRef}`)));
            const all = results.every(r => r.ok && (r.body.status === 'REFUNDED' || r.body.status === 'PAYMENT_FAILED'));
            if (all) {
                sessionStorage.setItem('cinemaseat_final', JSON.stringify(results.map(r => r.body)));
                location.reload();
                return;
            }
        }
        result.innerHTML += `<p class="muted">Timed out, but refund is processing. Click "Book more" to start over.</p>`;
    }

    // ---------------- Page router ----------------
    document.addEventListener('DOMContentLoaded', () => {
        const page = document.body.dataset.page;
        if (page === 'movies') initMoviesPage();
        else if (page === 'seats') initSeatsPage();
        else if (page === 'checkout') initCheckoutPage();
        else if (page === 'confirmation') initConfirmationPage();
    });

    return {
        initMoviesPage, initSeatsPage, initCheckoutPage, initConfirmationPage,
    };
})();
