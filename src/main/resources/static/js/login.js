$(window).on("scroll", () => {
    const $body = $("body");
    const scrollClass = "scroll";

    const $navbar = $("nav");
    const navbarClassDark = "navbar-dark";
    const navbarClassLight = "navbar-light";

    const $navbtns = $(".nav-btns");
    const navbtnsClassDark = "text-white";
    const navbtnsClassLight = "text-black";

    const $logo = $("#logo");
    const logoClassLight = "ebook-light";

    if (this.matchMedia("(min-width: 992px)").matches) {
        const scrollY = $(this).scrollTop();

        if (scrollY > 0) {
            $body.addClass(scrollClass);

            $navbar.removeClass(navbarClassDark);
            $navbar.addClass(navbarClassLight);

            $navbtns.removeClass(navbtnsClassDark);
            $navbtns.addClass(navbtnsClassLight);

            $logo.removeClass(logoClassLight);
        } else {
            $body.removeClass(scrollClass);

            $navbar.addClass(navbarClassDark);
            $navbar.removeClass(navbarClassLight);

            $navbtns.addClass(navbtnsClassDark);
            $navbtns.removeClass(navbtnsClassLight);

            $logo.addClass(logoClassLight);
        }
    } else {
        $body.removeClass(scrollClass);

        $navbar.removeClass(navbarClassDark);
        $navbar.addClass(navbarClassLight);

        $navbtns.removeClass(navbtnsClassDark);
        $navbtns.addClass(navbtnsClassLight);

        $logo.removeClass(logoClassLight);
    }
});

$(window).on('load', function () {
    if (window.location.href.indexOf('?error') > -1) {
        $('#fehlerModal').modal();
    }

    $("#login-form").submit(function (e) {
        const form = $("#login-form");
        const submit = $("#login-submit");
        const close = $("#login-close")

        if (form[0].checkValidity() === false) {
            e.preventDefault();
            e.stopPropagation();
        } else {
            submit.prop('disabled', true);
            close.prop('disabled', true);
            submit.prepend($('<span class="spinner-border spinner-border-sm mr-3" role="status" aria-hidden="true"></span>'));
        }
        form.addClass('was-validated');
    });

    if (!this.matchMedia("(min-width: 992px)").matches) {
        $("nav").removeClass("navbar-dark");
        $("nav").addClass("navbar-light");

        $(".nav-btns").removeClass("text-white");
        $(".nav-btns").addClass("text-black");

        $("#logo").removeClass("ebook-light");
    }

    $(".page-header .nav-link, .navbar-brand").on("click", function (e) {
        e.preventDefault();
        const href = $(this).attr("href");
        $("html, body").animate({
            scrollTop: $(href).offset().top - 71
        }, 600);
    });
});
