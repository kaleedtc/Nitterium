const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');
const script = readFileSync(`${__dirname}/../../main/assets/reading-position.js`, 'utf8');

class Storage {
    getItem(key) { return this[key] || null; }
    setItem(key, value) { this[key] = value; }
    removeItem(key) { delete this[key]; }
}

// A deterministic browser harness exercises the shipped script and layout changes.
function page(storage = new Storage(), state = null) {
    const events = {};
    const timers = new Map();
    let nextTimer = 0;
    let time = 10000;
    const list = { innerHTML: '<posts loaded="50"></posts>' };
    const context = {
        history: { state, replaceState(value) { this.state = value; } },
        sessionStorage: storage,
        scrollX: 0,
        scrollY: 0,
        shift: 0,
        Math,
        Date: { now: () => time },
        addEventListener(event, callback) { (events[event] ||= []).push(callback); },
        setTimeout(callback) { const id = ++nextTimer; timers.set(id, callback); return id; },
        clearTimeout(id) { timers.delete(id); },
        requestAnimationFrame(callback) { const id = ++nextTimer; timers.set(id, callback); return id; },
        scrollTo(x, y) { context.scrollX = x; context.scrollY = y; }
    };
    const rows = Array.from({ length: 50 }, (_, i) => {
        const row = {
            getBoundingClientRect: () => ({ top: i * 200 + context.shift - context.scrollY, bottom: (i + 1) * 200 + context.shift - context.scrollY }),
            querySelector: () => link
        };
        const link = { getAttribute: () => `/alice/status/${i + 1}`, closest: () => row };
        row.link = link;
        return row;
    });
    context.document = {
        querySelector: () => list,
        querySelectorAll: selector => selector === '.timeline-item' ? rows : rows.map(row => row.link),
        addEventListener: context.addEventListener
    };
    context.window = context;
    vm.createContext(context);
    vm.runInContext(script, context);
    return {
        context, list,
        fire(event) { (events[event] || []).forEach(callback => callback({ target: { closest: () => ({}) } })); },
        tick() { time += 150; const current = [...timers.values()]; timers.clear(); current.forEach(callback => callback()); }
    };
}

test('restores loaded posts and the visible tweet as late images change layout', () => {
    const first = page();
    first.context.scrollY = 800;
    first.fire('click');
    const back = page(first.context.sessionStorage, first.context.history.state);
    back.context.shift = 80;
    back.tick();
    assert.equal(back.context.scrollY, 880);
    assert.equal(back.list.innerHTML, first.list.innerHTML);
    back.context.shift = 200;
    back.tick();
    assert.equal(back.context.scrollY, 1000);
    back.fire('touchstart');
    back.context.scrollY = 777;
    back.tick();
    assert.equal(back.context.scrollY, 777);
});

test('keeps separate reading positions for separate history entries at the same URL', () => {
    const first = page();
    first.context.scrollY = 400;
    first.fire('pagehide');
    const second = page(first.context.sessionStorage);
    second.context.scrollY = 1200;
    second.fire('pagehide');
    assert.notEqual(first.context.history.state.nitteriumEntry, second.context.history.state.nitteriumEntry);
    const back = page(first.context.sessionStorage, first.context.history.state);
    back.tick();
    assert.equal(back.context.scrollY, 400);
});

test('storage errors do not prevent navigation or crash the injected script', () => {
    const brokenStorage = { getItem() { throw Error('disabled'); }, setItem() { throw Error('full'); } };
    const view = page(brokenStorage);
    assert.doesNotThrow(() => view.fire('pagehide'));
});
