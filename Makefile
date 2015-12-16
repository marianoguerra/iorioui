.PHONY: compile release build clean update install-om

build: compile release

om:
	git clone https://github.com/omcljs/om.git

install-om: om
	cd om && git checkout -- pom.xml
	cd om && git checkout master
	cd om && git pull --rebase origin master
	cd om && git checkout 71ee08e2b0d8be9d90d3d4eee81781112cc947ac
	cd om && lein install

compile: install-om 
	lein clean
	lein cljsbuild once prod

release:
	rm -rf release
	mkdir -p release/js/compiled/
	cp -r resources/public/css release/css
	cp resources/public/js/compiled/iorioui.js release/js/compiled/
	cp resources/public/index.html release/

clean:
	rm -rf release
	lein clean
	rm *-init.clj

update:
	git pull --rebase origin master
