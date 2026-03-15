(function () {
    function enhanceSheetWrap(wrap) {
        if (!wrap || wrap.dataset.scrollEnhanced === "1") return;
        wrap.dataset.scrollEnhanced = "1";

        const controls = document.createElement("div");
        controls.className = "table-scroll-controls";
        controls.innerHTML = `
            <button type="button" data-scroll-left title="Прокрутить влево">◀</button>
            <input type="range" min="0" max="100" value="0" data-scroll-range aria-label="Прокрутка таблицы">
            <button type="button" data-scroll-right title="Прокрутить вправо">▶</button>
        `;

        wrap.parentNode.insertBefore(controls, wrap);

        const leftBtn = controls.querySelector("[data-scroll-left]");
        const rightBtn = controls.querySelector("[data-scroll-right]");
        const range = controls.querySelector("[data-scroll-range]");

        const syncFromScroll = () => {
            const max = Math.max(wrap.scrollWidth - wrap.clientWidth, 0);
            range.value = max === 0 ? 0 : Math.round((wrap.scrollLeft / max) * 100);
            controls.classList.toggle("disabled", max === 0);
        };

        const scrollByStep = (dir) => {
            wrap.scrollBy({ left: dir * Math.max(220, Math.round(wrap.clientWidth * 0.6)), behavior: "smooth" });
        };

        leftBtn.addEventListener("click", () => scrollByStep(-1));
        rightBtn.addEventListener("click", () => scrollByStep(1));
        range.addEventListener("input", () => {
            const max = Math.max(wrap.scrollWidth - wrap.clientWidth, 0);
            wrap.scrollLeft = (Number(range.value) / 100) * max;
        });

        wrap.addEventListener("scroll", syncFromScroll, { passive: true });
        window.addEventListener("resize", syncFromScroll);
        syncFromScroll();
    }

    function init() {
        document.querySelectorAll(".sheet-wrap").forEach(enhanceSheetWrap);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
