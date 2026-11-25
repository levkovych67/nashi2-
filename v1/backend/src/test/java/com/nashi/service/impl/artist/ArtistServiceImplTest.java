package com.nashi.service.impl.artist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nashi.api.exception.ResourceNotFoundException;
import com.nashi.domain.Artist;
import com.nashi.domain.ArtistWork;
import com.nashi.domain.ImageCredit;
import com.nashi.domain.Member;
import com.nashi.dto.artist.ArtistCreateRequestDTO;
import com.nashi.dto.artist.ArtistDTOMinified;
import com.nashi.dto.artist.ArtistViewDTO;
import com.nashi.enums.ArtistCategory;
import com.nashi.enums.ArtistSource;
import com.nashi.enums.ArtistStatus;
import com.nashi.enums.LocationBasis;
import com.nashi.enums.Region;
import com.nashi.repo.ArtistRepository;
import com.nashi.service.DataValidationService;
import com.nashi.service.GeoCoordinateService;
import com.nashi.service.S3Service;
import com.nashi.service.impl.AiRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtistServiceImplTest {

    private ArtistRepository artistRepository;
    private S3Service s3Service;
    private DataValidationService dataValidationService;
    private ArtistServiceImpl service;

    @BeforeEach
    void setUp() {
        artistRepository = mock(ArtistRepository.class);
        s3Service = mock(S3Service.class);
        dataValidationService = mock(DataValidationService.class);
        service = new ArtistServiceImpl(
                artistRepository,
                s3Service,
                mock(AiRequestService.class),
                new ObjectMapper(),
                dataValidationService,
                mock(GeoCoordinateService.class));
    }

    @Test
    void suggestionGetsAFreeSlugWhenTheNameIsTaken() {
        MultipartFile photo = new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(dataValidationService.validateImages(any())).thenReturn(List.of(photo));
        when(s3Service.uploadFile(any(), anyString())).thenReturn("artists/images/photo.jpg");
        when(artistRepository.existsBySlug("океан-ельзи")).thenReturn(true);
        when(artistRepository.existsBySlug("океан-ельзи-2")).thenReturn(true);
        when(artistRepository.save(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));

        Artist saved = service.createSuggestion(suggestion("Океан Ельзи"), List.of(photo), List.of());

        assertEquals("океан-ельзи-3", saved.getSlug());
        // The free slug is found before anything is uploaded.
        InOrder order = inOrder(artistRepository, s3Service);
        order.verify(artistRepository).existsBySlug("океан-ельзи-3");
        order.verify(s3Service).uploadFile(photo, "artists/images");
        order.verify(artistRepository).save(any(Artist.class));
    }

    @Test
    void suggestionWithAFreeNameKeepsThePlainSlug() {
        when(artistRepository.save(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));

        Artist saved = service.createSuggestion(suggestion("Новий гурт"), List.of(), List.of());

        assertEquals("новий-гурт", saved.getSlug());
    }

    @Test
    void suggestionWhoseNameHasNoSlugLettersFallsBackToArtist() {
        when(artistRepository.existsBySlug("artist")).thenReturn(true);
        when(artistRepository.save(any(Artist.class))).thenAnswer(inv -> inv.getArgument(0));

        Artist saved = service.createSuggestion(suggestion("★ ★"), List.of(), List.of());

        assertEquals("artist-2", saved.getSlug());
    }

    private static ArtistCreateRequestDTO suggestion(String name) {
        return ArtistCreateRequestDTO.builder()
                .name(name)
                .style("Рок")
                .region(Region.KYIV)
                .city("Київ")
                .latitude(50.45)
                .longitude(30.52)
                .contactEmail("band@example.com")
                .build();
    }

    @Test
    void getAllArtistsSkipsCoordinatesWhenArtistHasNoLocation() {
        Artist artist = Artist.builder().id("a1").name("Без місця").slug("bez-mistsia").status(ArtistStatus.APPROVED).build();
        when(artistRepository.findByStatus(eq(ArtistStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artist)));

        Page<ArtistDTOMinified> page = service.getAllArtists(null, PageRequest.of(0, 20));

        ArtistDTOMinified dto = page.getContent().get(0);
        assertEquals("Без місця", dto.getName());
        assertNull(dto.getLatitude());
        assertNull(dto.getLongitude());
    }

    @Test
    void viewDtoExposesWikipediaSourceLinkAndImageCredit() {
        ImageCredit credit = ImageCredit.builder()
                .author("Автор")
                .license("CC BY-SA 4.0")
                .licenseUrl("https://creativecommons.org/licenses/by-sa/4.0")
                .filePageUrl("https://commons.wikimedia.org/wiki/File:Okean_Elzy.jpg")
                .build();
        Artist artist = Artist.builder()
                .id("a2")
                .name("Океан Ельзи")
                .wikiTitle("Океан Ельзи")
                .wikidataId("Q1328549")
                .source(ArtistSource.WIKIPEDIA)
                .imageCredit(credit)
                .status(ArtistStatus.APPROVED)
                .build();
        when(artistRepository.findById("a2")).thenReturn(Optional.of(artist));

        ArtistViewDTO dto = service.getArtistById("a2");

        assertEquals(ArtistSource.WIKIPEDIA, dto.getSource());
        assertEquals("https://uk.wikipedia.org/wiki/%D0%9E%D0%BA%D0%B5%D0%B0%D0%BD_%D0%95%D0%BB%D1%8C%D0%B7%D0%B8",
                dto.getWikiUrl());
        assertEquals(credit, dto.getImageCredit());
    }

    @Test
    void viewDtoHasNoWikiUrlForArtistsAddedThroughTheForm() {
        Artist artist = Artist.builder().id("a3").name("Гурт з форми").status(ArtistStatus.APPROVED).build();
        when(artistRepository.findById("a3")).thenReturn(Optional.of(artist));

        ArtistViewDTO dto = service.getArtistById("a3");

        assertNull(dto.getSource());
        assertNull(dto.getWikiUrl());
        assertNull(dto.getWikidataUrl());
        assertNull(dto.getImageCredit());
    }

    @Test
    void viewDtoCarriesNewFields() {
        ArtistWork kobzar = ArtistWork.builder()
                .wikidataId("Q11111111")
                .title("Кобзар")
                .year(1840)
                .imageUrl("https://upload.wikimedia.org/thumb/kobzar-320px.jpg")
                .fullImageUrl("https://upload.wikimedia.org/kobzar.jpg")
                .imageCredit(ImageCredit.builder().author("Невідомий").license("Public domain").build())
                .articleUrl("https://uk.wikipedia.org/wiki/%D0%9A%D0%BE%D0%B1%D0%B7%D0%B0%D1%80")
                .build();
        Artist artist = Artist.builder()
                .id("a4")
                .name("Тарас Шевченко")
                .wikidataId("Q134958")
                .categories(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST))
                .birthDate("1814-03-09")
                .deathDate("1861-03-10")
                .birthPlace("Моринці")
                .locationBasis(LocationBasis.BIRTH)
                .genres(List.of("поезія", "живопис"))
                .works(List.of(kobzar))
                .status(ArtistStatus.APPROVED)
                .build();
        when(artistRepository.findById("a4")).thenReturn(Optional.of(artist));

        ArtistViewDTO dto = service.getArtistById("a4");

        assertEquals(List.of(ArtistCategory.WRITER, ArtistCategory.VISUAL_ARTIST), dto.getCategories());
        assertEquals("1814-03-09", dto.getBirthDate());
        assertEquals("1861-03-10", dto.getDeathDate());
        assertEquals("Моринці", dto.getBirthPlace());
        assertEquals(LocationBasis.BIRTH, dto.getLocationBasis());
        assertEquals(List.of("поезія", "живопис"), dto.getGenres());
        assertEquals(List.of(kobzar), dto.getWorks());
        assertEquals("https://www.wikidata.org/wiki/Q134958", dto.getWikidataUrl());
    }

    @Test
    void viewDtoDerivesCategoriesAndGenresForFormArtists() {
        Artist band = Artist.builder()
                .id("a5")
                .name("Гурт з форми")
                .style("Рок")
                .members(List.of(Member.builder().firstName("Святослав").role("вокал").build()))
                .status(ArtistStatus.APPROVED)
                .build();
        when(artistRepository.findById("a5")).thenReturn(Optional.of(band));

        ArtistViewDTO dto = service.getArtistById("a5");

        assertEquals(List.of(ArtistCategory.BAND), dto.getCategories());
        assertEquals(List.of("Рок"), dto.getGenres());
        assertEquals(List.of(), dto.getWorks());
        assertNull(dto.getLocationBasis());
    }

    @Test
    void minifiedDtoCarriesCategories() {
        Artist writer = Artist.builder().id("a6").name("Леся Українка").categories(List.of(ArtistCategory.WRITER)).build();
        Artist solo = Artist.builder().id("a7").name("Соло з форми").build();
        when(artistRepository.findByStatus(eq(ArtistStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(writer, solo)));

        List<ArtistDTOMinified> content = service.getAllArtists(null, PageRequest.of(0, 20)).getContent();

        assertEquals(List.of(ArtistCategory.WRITER), content.get(0).getCategories());
        assertEquals(List.of(ArtistCategory.MUSICIAN), content.get(1).getCategories());
    }

    @Test
    void pendingArtistBySlugIsNotFound() {
        when(artistRepository.findBySlug("novyi-hurt")).thenReturn(Optional.of(
                Artist.builder().id("p1").name("Новий гурт").slug("novyi-hurt").status(ArtistStatus.PENDING).build()));
        when(artistRepository.findBySlug("prykhovanyi")).thenReturn(Optional.of(
                Artist.builder().id("p2").name("Прихований").slug("prykhovanyi").status(ArtistStatus.HIDDEN).build()));
        when(artistRepository.findBySlug("okean-elzy")).thenReturn(Optional.of(
                Artist.builder().id("p3").name("Океан Ельзи").slug("okean-elzy").status(ArtistStatus.APPROVED).build()));

        assertThrows(ResourceNotFoundException.class, () -> service.getArtistBySlug("novyi-hurt"));
        assertThrows(ResourceNotFoundException.class, () -> service.getArtistBySlug("prykhovanyi"));
        assertThrows(ResourceNotFoundException.class, () -> service.getArtistBySlug("nemaie"));
        assertEquals("Океан Ельзи", service.getArtistBySlug("okean-elzy").getName());
    }

    @Test
    void pendingArtistByIdIsNotFound() {
        when(artistRepository.findById("p1")).thenReturn(Optional.of(
                Artist.builder().id("p1").name("Новий гурт").status(ArtistStatus.PENDING).build()));
        when(artistRepository.findById("p2")).thenReturn(Optional.of(
                Artist.builder().id("p2").name("Прихований").status(ArtistStatus.HIDDEN).build()));

        ResourceNotFoundException pending = assertThrows(ResourceNotFoundException.class,
                () -> service.getArtistById("p1"));
        ResourceNotFoundException missing = assertThrows(ResourceNotFoundException.class,
                () -> service.getArtistById("p9"));
        assertThrows(ResourceNotFoundException.class, () -> service.getArtistById("p2"));
        // A pending artist looks exactly like a missing one.
        assertEquals(missing.getMessage().replace("p9", "p1"), pending.getMessage());
    }

    @Test
    void listAndSearchUseApprovedOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        when(artistRepository.findByStatus(any(), any())).thenReturn(Page.empty());
        when(artistRepository.findByRegionAndStatus(any(), any(), any())).thenReturn(Page.empty());
        when(artistRepository.findByNameContainingIgnoreCaseAndStatus(anyString(), any(), any())).thenReturn(Page.empty());

        service.getAllArtists(null, pageable);
        service.getAllArtists(Region.LVIV, pageable);
        service.searchArtists("океан", pageable);
        service.searchArtists("  ", pageable);

        verify(artistRepository, times(2)).findByStatus(ArtistStatus.APPROVED, pageable);
        verify(artistRepository).findByRegionAndStatus(Region.LVIV, ArtistStatus.APPROVED, pageable);
        verify(artistRepository).findByNameContainingIgnoreCaseAndStatus("океан", ArtistStatus.APPROVED, pageable);
        verify(artistRepository, never()).findAll(any(Pageable.class));
        verify(artistRepository, never()).findByRegion(any(), any());
    }
}
