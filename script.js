// Переключение между первой и второй страницами (SPA-навигация)
function switchPage(targetId) {
    const pages = document.querySelectorAll('.page');
    pages.forEach(page => page.classList.remove('active'));

    const targetPage = document.getElementById(targetId);
    if (targetPage) {
        targetPage.classList.add('active');
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}

// Анимация ночного звездного неба и падающих звезд на HTML5 Canvas
const canvas = document.getElementById('nightSkyCanvas');
const ctx = canvas.getContext('2d');

let stars = [];
let shootingStars = [];

function resizeCanvas() {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    initStars();
}

function initStars() {
    stars = [];
    const count = Math.floor((canvas.width * canvas.height) / 6500);
    const colors = ['#ffffff', '#f5e3c8', '#d7b377', '#c97a6e'];

    for (let i = 0; i < count; i++) {
        stars.push({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            radius: Math.random() * 1.5 + 0.3,
            alpha: Math.random(),
            speed: Math.random() * 0.012 + 0.003,
            color: colors[Math.floor(Math.random() * colors.length)]
        });
    }
}

function spawnShootingStar() {
    if (Math.random() < 0.008 && shootingStars.length < 2) {
        shootingStars.push({
            x: Math.random() * canvas.width * 0.8,
            y: Math.random() * canvas.height * 0.35,
            length: Math.random() * 70 + 40,
            speed: Math.random() * 6 + 5,
            alpha: 1
        });
    }
}

function animateSky() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Мерцающие звезды
    for (let s of stars) {
        s.alpha += s.speed;
        if (s.alpha >= 1 || s.alpha <= 0.15) {
            s.speed = -s.speed;
        }
        ctx.save();
        ctx.globalAlpha = Math.max(0.1, Math.min(1, s.alpha));
        ctx.fillStyle = s.color;
        ctx.beginPath();
        ctx.arc(s.x, s.y, s.radius, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
    }

    // Падающие звезды 
    spawnShootingStar();
    for (let i = shootingStars.length - 1; i >= 0; i--) {
        let st = shootingStars[i];
        ctx.save();
        let grad = ctx.createLinearGradient(st.x, st.y, st.x - st.length, st.y - st.length * 0.6);
        grad.addColorStop(0, `rgba(235, 215, 185, ${st.alpha})`);
        grad.addColorStop(1, 'rgba(235, 215, 185, 0)');
        ctx.strokeStyle = grad;
        ctx.lineWidth = 1.6;
        ctx.beginPath();
        ctx.moveTo(st.x, st.y);
        ctx.lineTo(st.x - st.length, st.y - st.length * 0.6);
        ctx.stroke();
        ctx.restore();

        st.x += st.speed;
        st.y += st.speed * 0.6;
        st.alpha -= 0.018;

        if (st.alpha <= 0) {
            shootingStars.splice(i, 1);
        }
    }

    requestAnimationFrame(animateSky);
}

window.addEventListener('resize', resizeCanvas);
resizeCanvas();
animateSky();